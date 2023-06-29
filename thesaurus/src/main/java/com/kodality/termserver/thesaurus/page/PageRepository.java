package com.kodality.termserver.thesaurus.page;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import java.util.Map;
import javax.inject.Singleton;

import static com.kodality.termserver.thesaurus.page.PageQueryParams.Ordering;

@Singleton
public class PageRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Page.class);
  private final static String select = """
      select
          p.*,
             (not exists(select 1 from thesaurus.page_link pl where pl.source_id != pl.target_id and pl.source_id = p.id and pl.sys_status = 'A')) as leaf,
             (select date from sys.provenance where target ->> 'type' = 'Page' and target ->> 'id' = p.id::text and activity ='created' order by id desc limit 1) created_at,
             (select author ->> 'id' from sys.provenance where target ->> 'type' = 'Page' and target ->> 'id' = p.id::text and activity ='created' order by id desc limit 1) created_by,
             (select date from sys.provenance where target ->> 'type' = 'Page' and target ->> 'id' = p.id::text and activity ='modified' order by id desc limit 1) modified_at,
             (select author ->> 'id' from sys.provenance where target ->> 'type' = 'Page' and target ->> 'id' = p.id::text and activity ='modified' order by id desc limit 1) modified_by
      """;
  private final Map<String, String> orderMapping = Map.of(
      Ordering.modified, "(select date from sys.provenance where target ->> 'type' = 'Page' and target ->> 'id' = p.id::text and activity in ('created', 'modified') order by id desc limit 1)"
  );

  public void save(Page page) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", page.getId());
    ssb.property("status", page.getStatus());
    ssb.property("template_id", page.getTemplateId());
    ssb.property("space_id", page.getSpaceId());
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
      SqlBuilder sb = new SqlBuilder("select count(1) from thesaurus.page p");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from thesaurus.page p");
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(PageQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where p.sys_status = 'A'");
    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("id", p, Long::valueOf));
    sb.appendIfNotNull(params.getIdsNe(), (s, p) -> s.and().notIn("id", p, Long::valueOf));
    sb.appendIfNotNull(params.getSpaceIds(), (s, p) -> s.and().in("space_id", p, Long::valueOf));
    sb.appendIfNotNull(params.getSlugs(), (s, p) -> s.and("exists(select 1 from thesaurus.page_content pc where pc.page_id = p.id and pc.sys_status = 'A'").and().in("pc.slug", p).append(")"));
    sb.appendIfNotNull("and exists(select 1 from thesaurus.page_content pc where pc.page_id = p.id and pc.sys_status = 'A' and pc.name ~* ?)", params.getTextContains());
    sb.appendIfNotNull("and exists(select 1 from thesaurus.page_link pl where pl.source_id = ? and pl.target_id = p.id and pl.target_id != pl.source_id and pl.sys_status = 'A')", params.getRootId());
    sb.appendIfTrue(params.isRoot(), "and exists(select 1 from thesaurus.page_link pl where  pl.source_id = p.id and pl.target_id = p.id and pl.sys_status = 'A')");
    return sb;
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("update thesaurus.page set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
