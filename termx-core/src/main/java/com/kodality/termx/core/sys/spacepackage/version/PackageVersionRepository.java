package com.kodality.termx.core.sys.spacepackage.version;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.sys.spacepackage.PackageVersion;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
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

    SqlBuilder sb = ssb.buildSave("sys.package_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public PackageVersion load(Long spaceId, Long packageId, Long versionId) {
    SqlBuilder sb = new SqlBuilder("select * from sys.package_version pv where id = ? and sys_status = 'A'", versionId);
    sb.append(" and exists (select 1 from sys.package p where p.id = pv.package_id and p.id = ? and p.space_id = ?)", packageId, spaceId);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public PackageVersion load(Long packageId, String version) {
    SqlBuilder sb = new SqlBuilder("select * from sys.package_version where package_id = ? and version = ? and sys_status = 'A'", packageId, version);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public List<PackageVersion> loadAll(Long spaceId, Long packageId) {
    SqlBuilder sb = new SqlBuilder("select pv.*");
    sb.append(", (select jsonb_agg(t.r) from (select json_build_object(" +
              "'id', pvr.id, 'resourceId', pvr.resource_id, 'resourceType', pvr.resource_type, 'terminologyServer', pvr.terminology_server" +
              ") as r ");
    sb.append("from sys.package_version_resource pvr where pvr.version_id = pv.id and pvr.sys_status = 'A') t) as resources");
    sb.append("from sys.package_version pv");
    sb.append("where sys_status = 'A'");
    sb.append("and exists (select 1 from sys.package p where p.id = pv.package_id and p.id = ? and p.space_id = ?)", packageId, spaceId);
    sb.append("order by version");
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public void delete(Long spaceId, Long packageId, Long versionId) {
    SqlBuilder sb = new SqlBuilder("update sys.package_version pv set sys_status = 'C' where id = ? and sys_status = 'A'", versionId);
    sb.append(" and exists (select 1 from sys.package p where p.id = pv.package_id and p.id = ? and p.space_id = ?)", packageId, spaceId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public PackageVersion loadLastVersion(String spaceCode, String packageCode) {
    SqlBuilder sb = new SqlBuilder("select pv.*");
    sb.append(
        ", (select jsonb_agg(t.r) from (select json_build_object('id', pvr.id, 'resourceId', pvr.resource_id, 'resourceType', pvr.resource_type, 'terminologyServer', pvr.terminology_server) as r ");
    sb.append("from sys.package_version_resource pvr where pvr.version_id = pv.id and pvr.sys_status = 'A') t) as resources");
    sb.append("from sys.package_version pv");
    sb.append("inner join sys.package p on p.id = pv.package_id and p.sys_status = 'A'");
    sb.append("inner join sys.space s on s.id = p.space_id and s.sys_status = 'A'");
    sb.append("where s.code = ? and p.code = ? and pv.sys_status = 'A' order by pv.version", spaceCode, packageCode);
    return getBean(sb.getSql(), bp, sb.getParams());
  }
}
