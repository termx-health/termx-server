package com.kodality.termx.wiki.pagelink;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.wiki.page.PageLink;
import com.kodality.termx.wiki.page.PageLinkQueryParams;
import com.kodality.termx.wiki.page.PageLinkQueryParams.Ordering;
import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class PageLinkRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PageLink.class);
  private final Map<String, String> orderMapping = Map.of(
      Ordering.orderNumber, "order_number"
  );

  public List<String> getPath(Long targetId) {
    SqlBuilder sb = new SqlBuilder("select plc.path from wiki.page_link_closure plc");
    sb.append("where plc.child_id = ?", targetId);
    sb.append("and plc.parent_id = ?", targetId);
    return jdbcTemplate.queryForList(sb.getSql(), String.class, sb.getParams());
  }

  public PageLink load(Long id) {
    String sql = "select * from wiki.page_link where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<PageLink> query(PageLinkQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from wiki.page_link pl");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from wiki.page_link pl");
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(PageLinkQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where pl.sys_status = 'A'");
    if (params.getRoot() != null) {
      sb.and().append(!params.getRoot() ? "not " : "").append("pl.source_id = pl.target_id");
    }
    if (StringUtils.isNotBlank(params.getSourceIds())) {
      sb.and().in("pl.source_id", params.getSourceIds(), Long::valueOf);
    }
    if (StringUtils.isNotBlank(params.getTargetIds())) {
      sb.and().in("pl.target_id", params.getTargetIds(), Long::valueOf);
    }
    if (StringUtils.isNotBlank(params.getSpaceIds())) {
      sb.append("and (");
      sb.append("exists(select 1 from wiki.page p where pl.source_id = p.id").and().in("p.space_id", params.getSpaceIds(), Long::valueOf).append(") and");
      sb.append("exists(select 1 from wiki.page p where pl.target_id = p.id").and().in("p.space_id", params.getSpaceIds(), Long::valueOf).append(")");
      sb.append(")");
    }
    if (params.getPermittedSpaceIds() != null) {
      sb.append("and (");
      sb.append("exists(select 1 from wiki.page p where pl.source_id = p.id").and().in("p.space_id", params.getPermittedSpaceIds()).append(") and");
      sb.append("exists(select 1 from wiki.page p where pl.target_id = p.id").and().in("p.space_id", params.getPermittedSpaceIds()).append(")");
      sb.append(")");
    }
    return sb;
  }


  public List<PageLink> loadRoots() {
    return getBeans("select * from wiki.page_link pl where pl.sys_status = 'A' and pl.source_id = pl.target_id order by order_number", bp);
  }

  public List<PageLink> loadTargets(Long sourceId) {
    return getBeans("select * from wiki.page_link pl where pl.sys_status = 'A' and pl.source_id != pl.target_id and pl.source_id = ? order by order_number", bp,
        sourceId);
  }

  public List<PageLink> loadSources(Long targetId) {
    return getBeans("select * from wiki.page_link pl where pl.sys_status = 'A' and pl.source_id != pl.target_id and pl.target_id = ? order by order_number", bp,
        targetId);
  }

  public List<PageLink> loadDescendants(Long sourceId) {
    return getBeans("""
        with flat as (
            select * from wiki.page_link pl where pl.sys_status = 'A'
        ),
        t as (
            with recursive rec as (
                select f.id, f.source_id, f.target_id
                    from flat f where f.target_id = ?
                union all
                select f.id, f.source_id, f.target_id
                    from flat f join rec r on f.source_id = r.target_id and f.source_id != f.target_id
            )
            select * from rec
        )
        select * from t
        """, bp, sourceId);
  }


  public void save(PageLink link) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", link.getId());
    ssb.property("source_id", link.getSourceId());
    ssb.property("target_id", link.getTargetId());
    ssb.property("order_number", link.getOrderNumber());
    SqlBuilder sb = ssb.buildSave("wiki.page_link", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    link.setId(id);
  }


  public void retainRoots(List<PageLink> links) {
    SqlBuilder sb = new SqlBuilder("update wiki.page_link set sys_status = 'C'");
    sb.append("where sys_status = 'A' and source_id = target_id");
    sb.andNotIn("id", links, PageLink::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void retainBySourceId(Long sourceId, List<PageLink> links) {
    SqlBuilder sb = new SqlBuilder("update wiki.page_link set sys_status = 'C'");
    sb.append("where sys_status = 'A' and source_id != target_id and source_id = ?", sourceId);
    sb.andNotIn("id", links, PageLink::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void retainByTargetId(List<PageLink> links, Long targetId) {
    SqlBuilder sb = new SqlBuilder("update wiki.page_link set sys_status = 'C'");
    sb.append("where sys_status = 'A' and target_id = ?", targetId);
    sb.andNotIn("id", links, PageLink::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }


  public void close(List<Long> ids) {
    SqlBuilder sb = new SqlBuilder("update wiki.page_link set sys_status = 'C' where sys_status = 'A'").and().in("id", ids);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void close(Long id) {
    String sql = "update wiki.page_link set sys_status = 'C' where id = ? and sys_status = 'A'";
    jdbcTemplate.update(sql, id);
  }


  public void refreshClosureView() {
    String sql = "select wiki.refresh_page_link_closure()";
    jdbcTemplate.queryForObject(sql, String.class);
  }
}
