package com.kodality.termserver.thesaurus;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import javax.inject.Singleton;

@Singleton
public class PageRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Page.class);

  private final static String select = "select p.*, (not exists(select 1 from thesaurus.page_relation pr where pr.source_id = p.id and pr.sys_status = 'A')) as leaf ";

  public void save(Page page) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", page.getId());
    ssb.property("status", page.getStatus());
    SqlBuilder sb = ssb.buildSave("thesaurus.page", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    page.setId(id);
  }

  public Page load(Long id) {
    String sql = select + "from thesaurus.page p where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<Page> query(PageQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from thesaurus.page p where p.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from thesaurus.page p where p.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(PageQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and id != ?", params.getIdNe());
    sb.appendIfNotNull("and exists(select 1 from thesaurus.page_relation pr where pr.source_id = ? and pr.target_id = p.id and pr.sys_status = 'A')", params.getRootId());
    sb.appendIfTrue(params.isRoot(),"and not exists(select 1 from thesaurus.page_relation pr where pr.target_id = p.id and pr.sys_status = 'A')");
    sb.appendIfNotNull("and exists(select 1 from thesaurus.page_content pc where pc.page_id = p.id and pc.sys_status = 'A' and pc.name ~* ?)", params.getTextContains());
    sb.appendIfNotNull("and exists(select 1 from thesaurus.page_content pc where pc.page_id = p.id and pc.sys_status = 'A' and pc.slug = ?)", params.getSlug());
    return sb;
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update thesaurus.page set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
