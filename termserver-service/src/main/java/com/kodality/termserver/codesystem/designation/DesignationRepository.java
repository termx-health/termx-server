package com.kodality.termserver.codesystem.designation;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.codesystem.Designation;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class DesignationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Designation.class);

  public List<Designation> loadAll(Long codeSystemEntityVersionId) {
    String sql = "select * from designation where sys_status = 'A' and code_system_entity_version_id = ?";
    return getBeans(sql, bp, codeSystemEntityVersionId);
  }

  public void retain(List<Designation> designations, Long codeSystemEntityVersionId) {
    SqlBuilder sb = new SqlBuilder("update designation set sys_status = 'C'");
    sb.append(" where code_system_entity_version_id = ? and sys_status = 'A'", codeSystemEntityVersionId);
    sb.andNotIn("id", designations, Designation::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void batchUpsert(List<Designation> designations, Long codeSystemEntityVersionId) {
    if (designations == null) {
      return;
    }
    designations.forEach(d -> {
      SqlBuilder sb =
          upsert(new SqlBuilder("insert into designation (" +
                  "code_system_entity_version_id, " +
                  "designation_type_id, " +
                  "name, " +
                  "language, " +
                  "rendering, " +
                  "preferred, " +
                  "case_significance, " +
                  "designation_kind, " +
                  "description, " +
                  "status) select ?,?,?,?,?,?,?,?,?,?",
                  codeSystemEntityVersionId,
                  d.getDesignationTypeId(),
                  d.getName(),
                  d.getLanguage(),
                  d.getRendering(),
                  d.isPreferred(),
                  d.getCaseSignificance(),
                  d.getDesignationKind(),
                  d.getDescription(),
                  d.getStatus()
              ),
              new SqlBuilder(
                  "UPDATE designation SET " +
                      "designation_type_id = ?, " +
                      "name = ?, " +
                      "language = ?, " +
                      "rendering = ?, " +
                      "preferred = ?, " +
                      "case_significance = ?, " +
                      "designation_kind = ?, " +
                      "description = ?, " +
                      "status = ? where code_system_entity_version_id = ? and id = ? and sys_status = 'A'",
                  d.getDesignationTypeId(),
                  d.getName(),
                  d.getLanguage(),
                  d.getRendering(),
                  d.isPreferred(),
                  d.getCaseSignificance(),
                  d.getDesignationKind(),
                  d.getDescription(),
                  d.getStatus(),
                  codeSystemEntityVersionId,
                  d.getId()));
      jdbcTemplate.update(sb.getSql(), sb.getParams());
    });
  }
}
