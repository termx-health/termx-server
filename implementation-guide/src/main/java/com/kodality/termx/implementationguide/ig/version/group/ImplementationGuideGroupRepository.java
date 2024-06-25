package com.kodality.termx.implementationguide.ig.version.group;

import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import java.util.List;
import jakarta.inject.Singleton;

@Singleton
public class ImplementationGuideGroupRepository extends BaseRepository {

  public void save(ImplementationGuideGroup group, String ig, Long versionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", group.getId());
    ssb.property("implementation_guide", ig);
    ssb.property("implementation_guide_version_id", versionId);
    ssb.property("name", group.getName());
    ssb.jsonProperty("description", group.getDescription());
    SqlBuilder sb = ssb.buildSave("sys.implementation_guide_group", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    group.setId(id);
  }


  public void retain(List<ImplementationGuideGroup> groups, String ig, Long versionId) {
    SqlBuilder sb = new SqlBuilder("update sys.implementation_guide_group set sys_status = 'C'");
    sb.append(" where implementation_guide = ? and implementation_guide_version_id = ? and sys_status = 'A'", ig, versionId);
    sb.andNotIn("id", groups, ImplementationGuideGroup::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
