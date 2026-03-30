package org.termx.modeler.structuredefinition;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class StructureDefinitionContentReferenceRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(StructureDefinitionContentReference.class);

  public void save(StructureDefinitionContentReference ref) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", ref.getId());
    ssb.property("structure_definition_version_id", ref.getStructureDefinitionVersionId());
    ssb.property("url", ref.getUrl());
    ssb.property("resource_type", ref.getResourceType());
    ssb.property("resource_id", ref.getResourceId());
    SqlBuilder sb = ssb.buildSave("modeler.structure_definition_content_reference", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    ref.setId(id);
  }

  public void deleteByVersionId(Long versionId) {
    SqlBuilder sb = new SqlBuilder("update modeler.structure_definition_content_reference set sys_status = 'C' where structure_definition_version_id = ? and sys_status = 'A'", versionId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<StructureDefinitionContentReference> loadByVersionId(Long versionId) {
    String sql = "select * from modeler.structure_definition_content_reference where sys_status = 'A' and structure_definition_version_id = ?";
    return getBeans(sql, bp, versionId);
  }

  public List<StructureDefinitionContentReference> loadByResourceTypeAndResourceId(String resourceType, String resourceId) {
    String sql = "select * from modeler.structure_definition_content_reference where sys_status = 'A' and resource_type = ? and resource_id = ?";
    return getBeans(sql, bp, resourceType, resourceId);
  }

  public List<Long> findDistinctStructureDefinitionIds(String resourceType, String resourceId) {
    String sql = "select distinct v.structure_definition_id from modeler.structure_definition_content_reference r" +
        " join modeler.structure_definition_version v on v.id = r.structure_definition_version_id and v.sys_status = 'A'" +
        " where r.sys_status = 'A' and r.resource_type = ? and r.resource_id = ?";
    return jdbcTemplate.queryForList(sql, Long.class, resourceType, resourceId);
  }
}
