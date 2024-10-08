package com.kodality.termx.wiki.page;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.wiki.page.PageTreeItem.PageTreeItemContent;
import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;

import static com.kodality.termx.wiki.page.PageQueryParams.Ordering;

@Singleton
public class PageRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Page.class, p -> {
    p.addColumnProcessor("settings", PgBeanProcessor.fromJson());
  });
  private final PgBeanProcessor treeBp = new PgBeanProcessor(PageTreeItem.class, p -> {
    p.addColumnProcessor("contents", PgBeanProcessor.fromJson(JsonUtil.getMapType(PageTreeItemContent.class)));
  });

  private final static String select = """
      select
          p.*,
          (not exists(select 1 from wiki.page_link pl where pl.source_id != pl.target_id and pl.source_id = p.id and pl.sys_status = 'A')) as leaf
      """;

  private final Map<String, String> orderMapping = Map.of(
      Ordering.modified, "(select date from sys.provenance where target ->> 'type' = 'Page' and target ->> 'id' = p.id::text and activity in ('created', 'modified') order by id desc limit 1)"
  );

  public void save(Page page) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", page.getId());
    ssb.property("code", page.getCode());
    ssb.property("space_id", page.getSpaceId());
    ssb.jsonProperty("settings", page.getSettings());
    ssb.property("status", page.getStatus());

    SqlBuilder sb = ssb.buildSave("wiki.page", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    page.setId(id);
  }

  public Page load(Long id) {
    String sql = select + "from wiki.page p where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<Page> query(PageQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from wiki.page p");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from wiki.page p");
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(PageQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where p.sys_status = 'A'");
    sb.and().in("space_id", params.getPermittedSpaceIds());
    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("id", p, Long::valueOf));
    sb.appendIfNotNull(params.getIdsNe(), (s, p) -> s.and().notIn("id", p, Long::valueOf));
    sb.appendIfNotNull(params.getSpaceIds(), (s, p) -> s.and().in("space_id", p, Long::valueOf));
    sb.appendIfNotNull(params.getSlugs(), (s, p) -> s.and("exists(select 1 from wiki.page_content pc where pc.page_id = p.id and pc.sys_status = 'A'").and().in("pc.slug", p).append(")"));
    sb.appendIfNotNull(params.getCodes(), (s, p) -> s.and().in("code", p));
    sb.appendIfNotNull("and exists(select 1 from wiki.page_content pc where pc.page_id = p.id and pc.sys_status = 'A' and pc.name ~* ?)", params.getTextContains());
    sb.appendIfNotNull("and exists(select 1 from wiki.page_link pl where pl.source_id = ? and pl.target_id = p.id and pl.target_id != pl.source_id and pl.sys_status = 'A')", params.getRootId());
    sb.appendIfTrue(params.isRoot(), "and exists(select 1 from wiki.page_link pl where  pl.source_id = p.id and pl.target_id = p.id and pl.sys_status = 'A')");
    return sb;
  }

  public void delete(Long id) {
    SqlBuilder sb = new SqlBuilder("update wiki.page set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<PageTreeItem> loadTree(Long spaceId) {
    String sql = """
        with pages as (
          select p.id page_id, (case when pl.source_id = pl.target_id then null else pl.source_id end) parent_page_id, pl.order_number from wiki.page p
          left outer join wiki.page_link pl on pl.target_id  = p.id and pl.sys_status = 'A'
          where p.space_id = ? and p.sys_status = 'A'
        )
        select p.*,
        (select json_object_agg(pc.lang, jsonb_build_object('id', pc.id, 'slug', pc.slug, 'name', pc.name))
           from wiki.page_content pc where pc.page_id = p.page_id and pc.sys_status = 'A') contents
        from pages p
        order by p.order_number
        """;
    return getBeans(sql, treeBp, spaceId);
  }
}
