package org.termx.modeler.structuredefinition;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class StructureDefinitionVersionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(StructureDefinitionVersion.class, bp -> {
    bp.addColumnProcessor("description", PgBeanProcessor.fromJson());
  });

  public void save(StructureDefinitionVersion v) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", v.getId());
    ssb.property("structure_definition_id", v.getStructureDefinitionId());
    ssb.property("version", v.getVersion());
    ssb.property("fhir_id", v.getFhirId());
    ssb.property("content", v.getContent());
    ssb.property("content_type", v.getContentType());
    ssb.property("content_format", v.getContentFormat());
    ssb.property("status", v.getStatus());
    ssb.property("release_date", v.getReleaseDate());
    ssb.jsonProperty("description", v.getDescription());
    SqlBuilder sb = ssb.buildSave("modeler.structure_definition_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    v.setId(id);
  }

  public StructureDefinitionVersion load(Long id) {
    String sql = "select * from modeler.structure_definition_version where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public StructureDefinitionVersion load(Long structureDefinitionId, String version) {
    SqlBuilder sb = new SqlBuilder(
        "select * from modeler.structure_definition_version where sys_status = 'A' and structure_definition_id = ?");
    sb.append(" and ");
    if (version == null || version.isBlank()) {
      sb.append("version is null");
    } else {
      sb.append("version = ?", version);
    }
    List<StructureDefinitionVersion> list = version == null || version.isBlank()
        ? getBeans(sb.getSql(), bp, structureDefinitionId)
        : getBeans(sb.getSql(), bp, structureDefinitionId, version);
    return list.isEmpty() ? null : list.get(0);
  }

  /** Current version: latest by release_date or id. */
  public StructureDefinitionVersion loadCurrent(Long structureDefinitionId) {
    String sql = "select * from modeler.structure_definition_version where sys_status = 'A' and structure_definition_id = ? "
        + "order by release_date desc nulls last, id desc limit 1";
    return getBean(sql, bp, structureDefinitionId);
  }

  public List<StructureDefinitionVersion> listByStructureDefinition(Long structureDefinitionId) {
    String sql = "select * from modeler.structure_definition_version where sys_status = 'A' and structure_definition_id = ? order by release_date desc nulls last, id desc";
    return getBeans(sql, bp, structureDefinitionId);
  }

  public void cancel(Long id) {
    jdbcTemplate.update("update modeler.structure_definition_version set sys_status = 'C' where id = ? and sys_status = 'A'", id);
  }
}
