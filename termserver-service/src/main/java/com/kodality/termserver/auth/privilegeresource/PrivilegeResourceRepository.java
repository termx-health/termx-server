package com.kodality.termserver.auth.privilegeresource;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.auth.PrivilegeResource;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PrivilegeResourceRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PrivilegeResource.class);

  public void save(PrivilegeResource resource, Long privilegeId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", resource.getId());
    ssb.property("resource_type", resource.getResourceType());
    ssb.property("resource_id", resource.getResourceId());
    ssb.property("privilege_id", privilegeId);
    SqlBuilder sb = ssb.buildSave("auth.privilege_resource", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    resource.setId(id);
  }

  public List<PrivilegeResource> load(Long privilegeId) {
    String sql = "select * from auth.privilege_resource where privilege_id = ? and sys_status = 'A'";
    return getBeans(sql, bp, privilegeId);
  }

  public void retain(List<PrivilegeResource> resources, Long privilegeId) {
    SqlBuilder sb = new SqlBuilder("update auth.privilege_resource set sys_status = 'C'");
    sb.append(" where privilege_id = ? and sys_status = 'A'", privilegeId);
    sb.andNotIn("id", resources, PrivilegeResource::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
