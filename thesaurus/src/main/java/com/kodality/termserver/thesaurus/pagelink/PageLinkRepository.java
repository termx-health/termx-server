package com.kodality.termserver.thesaurus.pagelink;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.thesaurus.page.PageLink;
import com.kodality.termserver.thesaurus.page.PageLinkQueryParams;
import com.kodality.termserver.thesaurus.page.PageLinkQueryParams.Ordering;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class PageLinkRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PageLink.class);
  private final Map<String, String> orderMapping = Map.of(
      Ordering.orderNumber, "order_number"
  );

  public List<String> getPath(Long targetId) {
    SqlBuilder sb = new SqlBuilder("select plc.path from thesaurus.page_link_closure plc");
    sb.append("where plc.child_id = ?", targetId);
    sb.append("and plc.parent_id = ?", targetId);
    return jdbcTemplate.queryForList(sb.getSql(), String.class, sb.getParams());
  }

  public PageLink load(Long id) {
    String sql = "select * from thesaurus.page_link where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<PageLink> query(PageLinkQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from thesaurus.page_link pl");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from thesaurus.page_link pl");
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
    return sb;
  }


  public List<PageLink> loadRoots() {
    return getBeans("select * from thesaurus.page_link pl where pl.sys_status = 'A' and pl.source_id = pl.target_id order by order_number", bp);
  }

  public List<PageLink> loadTargets(Long sourceId) {
    return getBeans("select * from thesaurus.page_link pl where pl.sys_status = 'A' and pl.source_id = ? order by order_number", bp, sourceId);
  }

  public List<PageLink> loadSources(Long targetId) {
    return getBeans("select * from thesaurus.page_link pl where pl.sys_status = 'A' and pl.target_id = ? order by order_number", bp, targetId);
  }


  public void save(PageLink link) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", link.getId());
    ssb.property("source_id", link.getSourceId());
    ssb.property("target_id", link.getTargetId());
    ssb.property("order_number", link.getOrderNumber());
    SqlBuilder sb = ssb.buildSave("thesaurus.page_link", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    link.setId(id);
  }

  public void retainRoots(List<PageLink> links) {
    SqlBuilder sb = new SqlBuilder("update thesaurus.page_link set sys_status = 'C'");
    sb.append("where sys_status = 'A' and source_id = target_id");
    sb.andNotIn("id", links, PageLink::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void retainBySourceId(Long sourceId, List<PageLink> links) {
    SqlBuilder sb = new SqlBuilder("update thesaurus.page_link set sys_status = 'C'");
    sb.append("where sys_status = 'A' and source_id = ?", sourceId);
    sb.andNotIn("id", links, PageLink::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void retainByTargetId(List<PageLink> links, Long targetId) {
    SqlBuilder sb = new SqlBuilder("update thesaurus.page_link set sys_status = 'C'");
    sb.append("where  sys_status = 'A' and target_id = ?", targetId);
    sb.andNotIn("id", links, PageLink::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void close(Long id) {
    String sql = "update thesaurus.page_link set sys_status = 'C' where id = ? and sys_status = 'A'";
    jdbcTemplate.update(sql, id);
  }


  public void refreshClosureView() {
    String sql = "select thesaurus.refresh_page_link_closure()";
    jdbcTemplate.queryForObject(sql, String.class);
  }
}
