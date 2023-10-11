package com.kodality.termx.wiki.pagerelation;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.wiki.page.PageRelation;
import com.kodality.termx.wiki.page.PageRelationQueryParams;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PageRelationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PageRelation.class, p -> {
    p.addColumnProcessor("content", PgBeanProcessor.fromJson());
  });

  private final String select = """
      select
        pr.*,
        (select json_build_object('id', pc.id, 'code', pc.slug, 'names', json_build_object(pc.lang, pc.name))) content,
        pc.space_id
      from wiki.page_relation pr
        left join wiki.page_content pc on pc.id = pr.content_id
      """;

  public QueryResult<PageRelation> query(PageRelationQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from wiki.page_relation pr");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select);
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(PageRelationQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where pr.sys_status = 'A'");
    sb.appendIfNotNull("and pr.type = ?", params.getType());
    if (StringUtils.isNotEmpty(params.getTarget())) {
      sb.and().in("pr.target", params.getTarget());
    }
    return sb;
  }

  public List<PageRelation> loadAll(Long pageId) {
    String sql = select + "where pr.sys_status = 'A' and pr.page_id = ?";
    return getBeans(sql, bp, pageId);
  }

  public void save(PageRelation relation, Long pageId, Long contentId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", relation.getId());
    ssb.property("page_id", pageId);
    ssb.property("content_id", contentId);
    ssb.property("target", relation.getTarget());
    ssb.property("type", relation.getType());
    SqlBuilder sb = ssb.buildSave("wiki.page_relation", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    relation.setId(id);
  }

  public void retain(List<PageRelation> relations, Long pageId, Long contentId) {
    SqlBuilder sb = new SqlBuilder("update wiki.page_relation set sys_status = 'C'");
    sb.append(" where page_id = ? and content_id = ? and sys_status = 'A'", pageId, contentId);
    sb.andNotIn("id", relations, PageRelation::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void deleteByPage(Long pageId) {
    String sql = "update wiki.page_relation set sys_status = 'C' where sys_status = 'A' and page_id = ?";
    jdbcTemplate.update(sql, pageId);
  }
}
