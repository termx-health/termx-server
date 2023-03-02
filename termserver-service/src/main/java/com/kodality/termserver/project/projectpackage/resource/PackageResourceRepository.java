package com.kodality.termserver.project.projectpackage.resource;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.project.projectpackage.PackageVersion.PackageResource;
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

    SqlBuilder sb = ssb.buildSave("terminology.package_version_resource", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    resource.setId(id);
  }

  public void retain(List<PackageResource> resources, Long versionId) {
    SqlBuilder sb = new SqlBuilder("update terminology.package_version_resource set sys_status = 'C'");
    sb.append(" where version_id = ? and sys_status = 'A'", versionId);
    sb.andNotIn("id", resources, PackageResource::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<PackageResource> loadAll(String projectCode, String packageCode, String version) {
    SqlBuilder sb = new SqlBuilder("select distinct on (pvr.id) pvr.* from terminology.package_version_resource pvr");
    sb.append("left join terminology.package_version pv on pv.id = pvr.version_id and pv.sys_status = 'A'");
    sb.append("left join terminology.package p on p.id = pv.package_id and p.sys_status = 'A'");
    sb.append("left join terminology.project pr on pr.id = p.project_id and pr.sys_status = 'A'");
    sb.append("where pvr.sys_status = 'A'");
    sb.appendIfNotNull("and pr.code = ?", projectCode);
    sb.appendIfNotNull("and p.code = ?", packageCode);
    sb.appendIfNotNull("and pv.version = ?", version);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }
}
