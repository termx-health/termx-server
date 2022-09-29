package com.kodality.termserver.thesaurus.pagecontent;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PageContentRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PageContent.class);

  public void save(PageContent content, Long pageId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", content.getId());
    ssb.property("page_id", pageId);
    ssb.property("name", content.getName());
    ssb.property("slug", content.getSlug());
    ssb.property("lang", content.getLang());
    ssb.property("content", content.getContent());
    SqlBuilder sb = ssb.buildSave("thesaurus.page_content", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    content.setId(id);
  }

  public List<PageContent> loadAll(Long pageId) {
    String sql = "select * from thesaurus.page_content where sys_status = 'A' and page_id = ?";
    return getBeans(sql, bp, pageId);
  }

  public QueryResult<PageContent> query(PageContentQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from thesaurus.page_content pc where pc.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select pc.* from thesaurus.page_content pc where pc.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(PageContentQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and slug = ?", params.getSlug());
    return sb;
  }
}
