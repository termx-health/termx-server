package com.kodality.termx.core.sys.checklist.whitelist;

import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.sys.checklist.Checklist.ChecklistWhitelist;
import java.util.List;
import jakarta.inject.Singleton;

@Singleton
public class ChecklistWhitelistRepository extends BaseRepository {

  public void save(Long checklistId, ChecklistWhitelist w) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", w.getId());
    ssb.property("checklist_id", checklistId);
    ssb.property("resource_type", w.getResourceType());
    ssb.property("resource_id", w.getResourceId());
    ssb.property("resource_name", w.getResourceName());
    SqlBuilder sb = ssb.buildSave("sys.checklist_whitelist", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    w.setId(id);
  }


  public void retain(Long checklistId, List<ChecklistWhitelist> whitelist) {
    SqlBuilder sb = new SqlBuilder("update sys.checklist_whitelist set sys_status = 'C'");
    sb.append(" where checklist_id = ? and sys_status = 'A'", checklistId);
    sb.andNotIn("id", whitelist, ChecklistWhitelist::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
