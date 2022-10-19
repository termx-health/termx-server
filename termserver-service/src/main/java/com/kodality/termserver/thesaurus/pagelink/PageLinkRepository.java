package com.kodality.termserver.thesaurus.pagelink;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PageLinkRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PageLink.class);

  public void save(PageLink link, Long pageId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", link.getId());
    ssb.property("source_id", link.getSourceId());
    ssb.property("target_id", pageId);
    ssb.property("order_number", link.getOrderNumber());
    SqlBuilder sb = ssb.buildSave("thesaurus.page_link", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    link.setId(id);
  }

  public List<PageLink> loadAll(Long pageId) {
    String sql = "select * from thesaurus.page_link where sys_status = 'A' and target_id = ? order by order_number";
    return getBeans(sql, bp, pageId);
  }

  public void retain(List<PageLink> links, Long pageId) {
    SqlBuilder sb = new SqlBuilder("update thesaurus.page_link set sys_status = 'C'");
    sb.append(" where target_id = ? and sys_status = 'A'", pageId);
    sb.andNotIn("id", links, PageLink::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<String> getPath(Long pageId) {
    SqlBuilder sb = new SqlBuilder("select plc.path from thesaurus.page_link_closure plc");
    sb.append("where plc.child_id = ?", pageId);
    sb.append("and plc.parent_id = ?", pageId);
    return jdbcTemplate.queryForList(sb.getSql(), String.class, sb.getParams());
  }

  public void refreshClosureView() {
    String sql = "select thesaurus.refresh_page_link_closure()";
    jdbcTemplate.queryForObject(sql, String.class);
  }
}
