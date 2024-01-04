package com.kodality.termx.core.sys.spacepackage.resource;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PackageResourceRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PackageResource.class);

  public void save(Long versionId, PackageResource resource) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", resource.getId());
    ssb.property("version_id", versionId);
    ssb.property("resource_id", resource.getResourceId());
    ssb.property("resource_type", resource.getResourceType());
    ssb.property("terminology_server", resource.getTerminologyServer());

    SqlBuilder sb = ssb.buildSave("sys.package_version_resource", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    resource.setId(id);
  }

  public void retain(List<PackageResource> resources, Long versionId) {
    SqlBuilder sb = new SqlBuilder("update sys.package_version_resource set sys_status = 'C'");
    sb.append(" where version_id = ? and sys_status = 'A'", versionId);
    sb.andNotIn("id", resources, PackageResource::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void changeServer(List<Long> ids, String server) {
    SqlBuilder sb = new SqlBuilder("update sys.package_version_resource set terminology_server = ?", server);
    sb.append(" where sys_status = 'A'");
    sb.and().in("id", ids);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<PackageResource> loadAll(Long spaceId, String packageCode, String version) {
    SqlBuilder sb = new SqlBuilder("select distinct on (pvr.id) pvr.* from sys.package_version_resource pvr");
    sb.append("left join sys.package_version pv on pv.id = pvr.version_id and pv.sys_status = 'A'");
    sb.append("left join sys.package p on p.id = pv.package_id and p.sys_status = 'A'");
    sb.append("left join sys.space s on s.id = p.space_id and s.sys_status = 'A'");
    sb.append("where pvr.sys_status = 'A'");
    sb.append("and s.id = ?", spaceId);
    sb.appendIfNotNull("and p.code = ?", packageCode);
    sb.appendIfNotNull("and pv.version = ?", version);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public PackageResource load(Long id) {
    String sql = "select * from sys.package_version_resource pvr where pvr.id = ? and pvr.sys_status = 'A'";
    return getBean(sql, bp, id);
  }
}
