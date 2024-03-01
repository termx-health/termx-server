package com.kodality.termx.uam.privilegeresource;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.auth.PrivilegeResource;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PrivilegeResourceRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PrivilegeResource.class, bp -> {
    bp.addColumnProcessor("actions", PgBeanProcessor.fromJson());
  });

  public List<PrivilegeResource> load(Long privilegeId) {
    String sql = """
        select
          pr.*,
          (case when pr.resource_type IN ('Space', 'Wiki') then (select s.code from sys.space s where s.id = pr.resource_id::bigint) else null end) as resource_name 
        from uam.privilege_resource pr 
        where privilege_id = ? and sys_status = 'A'
        """;
    return getBeans(sql, bp, privilegeId);
  }

  public void save(PrivilegeResource resource, Long privilegeId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", resource.getId());
    ssb.property("resource_type", resource.getResourceType());
    ssb.property("resource_id", resource.getResourceId());
    ssb.property("privilege_id", privilegeId);
    ssb.jsonProperty("actions", resource.getActions());

    SqlBuilder sb = ssb.buildSave("uam.privilege_resource", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    resource.setId(id);
  }

  public void retain(List<PrivilegeResource> resources, Long privilegeId) {
    SqlBuilder sb = new SqlBuilder("update uam.privilege_resource set sys_status = 'C'");
    sb.append(" where privilege_id = ? and sys_status = 'A'", privilegeId);
    sb.andNotIn("id", resources, PrivilegeResource::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
