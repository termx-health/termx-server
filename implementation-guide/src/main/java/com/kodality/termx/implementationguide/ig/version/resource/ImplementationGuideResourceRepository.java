package com.kodality.termx.implementationguide.ig.version.resource;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class ImplementationGuideResourceRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ImplementationGuideResource.class, bp -> {
    bp.addColumnProcessor("group", PgBeanProcessor.fromJson());
  });

  private final static String select = "select igr.* , " +
            "(select json_build_object('id', igg.id, 'name', igg.name, 'description', igg.description) from sys.implementation_guide_group igg where igg.id = igr.group_id and igg.sys_status = 'A') as group ";

  public void save(ImplementationGuideResource resource, String ig, Long versionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", resource.getId());
    ssb.property("implementation_guide", ig);
    ssb.property("implementation_guide_version_id", versionId);
    ssb.property("type", resource.getType());
    ssb.property("reference", resource.getReference());
    ssb.property("version", resource.getVersion());
    ssb.property("name", resource.getName());
    ssb.property("group_id", resource.getGroup().getId());
    SqlBuilder sb = ssb.buildSave("sys.implementation_guide_resource", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    resource.setId(id);
  }


  public void retain(List<ImplementationGuideResource> resources, String ig, Long versionId) {
    SqlBuilder sb = new SqlBuilder("update sys.implementation_guide_resource set sys_status = 'C'");
    sb.append(" where implementation_guide = ? and implementation_guide_version_id = ? and sys_status = 'A'", ig, versionId);
    sb.andNotIn("id", resources, ImplementationGuideResource::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<ImplementationGuideResource> loadAll(Long versionId) {
    String sql = select + " from sys.implementation_guide_resource igr where igr.sys_status = 'A' and igr.implementation_guide_version_id = ?";
    return getBeans(sql, bp, versionId);
  }
}
