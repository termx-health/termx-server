package com.kodality.termx.wiki.pagecontent;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageContentQueryParams;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PageContentRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PageContent.class);

  public void save(PageContent content, Long pageId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", content.getId());
    ssb.property("page_id", pageId);
    ssb.property("space_id", content.getSpaceId());
    ssb.property("name", content.getName());
    ssb.property("slug", content.getSlug());
    ssb.property("lang", content.getLang());
    ssb.property("content", content.getContent());
    ssb.property("content_type", content.getContentType());
    SqlBuilder sb = ssb.buildSave("wiki.page_content", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    content.setId(id);
  }

  public PageContent load(Long id) {
    String sql = "select * from wiki.page_content where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public List<PageContent> loadAll(Long pageId) {
    String sql = "select * from wiki.page_content where sys_status = 'A' and page_id = ?";
    return getBeans(sql, bp, pageId);
  }

  public QueryResult<PageContent> query(PageContentQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from wiki.page_content pc where pc.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select pc.* from wiki.page_content pc where pc.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(PageContentQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull(params.getSlugs(), (s, p) -> s.and().in("pc.slug", p));
    sb.appendIfNotNull(params.getSpaceIds(), (s, p) -> s.and().in("pc.space_id", p, Long::valueOf));
    sb.appendIfNotNull(params.getTextContains(), (s, p) -> s.and("pc.name ~* ?", p));

    if (StringUtils.isNotEmpty(params.getRelations())) {
      String[] relations = params.getRelations().split(",");
      sb.and("( 1<>1");
      for (String relation: relations) {
        String[] r = PipeUtil.parsePipe(relation);
        sb.or("exists (select 1 from wiki.page_relation pr where pr.type = ? and pr.target = ? and pr.sys_status = 'A' and pr.content_id = pc.id)", r[0], r[1]);
      }
      sb.append(")");
    }
    return sb;
  }
}
