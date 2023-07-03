package com.kodality.termx.thesaurus.pagerelation;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.thesaurus.page.PageRelation;
import com.kodality.termx.thesaurus.page.PageRelationQueryParams;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PageRelationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PageRelation.class, p -> {
    p.addColumnProcessor("content_id","content",PgBeanProcessor.toIdCodeName());
  });

  public void save(PageRelation relation, Long pageId, Long contentId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", relation.getId());
    ssb.property("page_id", pageId);
    ssb.property("content_id", contentId);
    ssb.property("target", relation.getTarget());
    ssb.property("type", relation.getType());
    SqlBuilder sb = ssb.buildSave("thesaurus.page_relation", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    relation.setId(id);
  }

  public List<PageRelation> loadAll(Long pageId) {
    String sql = "select * from thesaurus.page_relation where sys_status = 'A' and page_id = ?";
    return getBeans(sql, bp, pageId);
  }

  public void retain(List<PageRelation> relations, Long pageId, Long contentId) {
    SqlBuilder sb = new SqlBuilder("update thesaurus.page_relation set sys_status = 'C'");
    sb.append(" where page_id = ? and content_id = ? and sys_status = 'A'", pageId, contentId);
    sb.andNotIn("id", relations, PageRelation::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public QueryResult<PageRelation> query(PageRelationQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from thesaurus.page_relation pr where pr.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from thesaurus.page_relation pr where pr.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(PageRelationQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and type = ?", params.getType());
    if (StringUtils.isNotEmpty(params.getTarget())) {
      sb.and().in("target", params.getTarget());
    }
    return sb;
  }
}
