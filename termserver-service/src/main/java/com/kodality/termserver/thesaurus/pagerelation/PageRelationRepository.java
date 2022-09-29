package com.kodality.termserver.thesaurus.pagerelation;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PageRelationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PageRelation.class);

  public void save(PageRelation relation, Long pageId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", relation.getId());
    ssb.property("source_id", relation.getSourceId());
    ssb.property("target_id", pageId);
    ssb.property("order_number", relation.getOrderNumber());
    SqlBuilder sb = ssb.buildSave("thesaurus.page_relation", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    relation.setId(id);
  }

  public List<PageRelation> loadAll(Long pageId) {
    String sql = "select * from thesaurus.page_relation where sys_status = 'A' and target_id = ?";
    return getBeans(sql, bp, pageId);
  }

  public void retain(List<PageRelation> relations, Long pageId) {
    SqlBuilder sb = new SqlBuilder("update thesaurus.page_relation set sys_status = 'C'");
    sb.append(" where target_id = ? and sys_status = 'A'", pageId);
    sb.andNotIn("id", relations, PageRelation::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<String> getPath(Long pageId) {
    SqlBuilder sb = new SqlBuilder("select prc.path from thesaurus.page_relation_closure prc");
    sb.append("where prc.child_id = ?", pageId);
    sb.append("and prc.parent_id = ?", pageId);
    return jdbcTemplate.queryForList(sb.getSql(), String.class, sb.getParams());
  }

  public void refreshClosureView() {
    String sql = "select thesaurus.refresh_page_relation_closure()";
    jdbcTemplate.queryForObject(sql, String.class);
  }
}
