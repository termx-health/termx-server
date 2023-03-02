package com.kodality.termserver.project.projectpackage.version;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.project.projectpackage.PackageVersion;
import com.kodality.termserver.project.projectpackage.PackageVersion.PackageResource;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PackageVersionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(PackageVersion.class, p -> {
    p.addColumnProcessor("resources", PgBeanProcessor.fromJson(JsonUtil.getListType(PackageResource.class)));
  });

  public void save(Long packageId, PackageVersion version) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("package_id", packageId);
    ssb.property("version", version.getVersion());
    ssb.property("description", version.getDescription());

    SqlBuilder sb = ssb.buildSave("terminology.package_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public PackageVersion load(Long id) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.package_version where id = ? and sys_status = 'A'", id);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public List<PackageVersion> loadAll(Long packageId) {
    SqlBuilder sb = new SqlBuilder("select pv.*");
    sb.append(", (select jsonb_agg(t.r) from (select json_build_object('id', pvr.id, 'resourceId', pvr.resource_id, 'resourceType', pvr.resource_type, 'terminologyServer', pvr.terminology_server) as r ");
    sb.append("from terminology.package_version_resource pvr where pvr.version_id = pv.id and pvr.sys_status = 'A') t) as resources");
    sb.append("from terminology.package_version pv");
    sb.append("where package_id = ? and sys_status = 'A' order by version", packageId);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public void delete(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.package_version set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
