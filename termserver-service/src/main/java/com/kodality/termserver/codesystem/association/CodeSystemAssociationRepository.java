package com.kodality.termserver.codesystem.association;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class CodeSystemAssociationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemAssociation.class, bp -> {
    bp.overrideColumnMapping("target_code_system_entity_version_id", "targetId");
  });

  public List<CodeSystemAssociation> loadAll(Long codeSystemEntityVersionId) {
    String sql = "select * from code_system_association where sys_status = 'A' and source_code_system_entity_version_id = ?";
    return getBeans(sql, bp, codeSystemEntityVersionId);
  }

  public void retain(List<CodeSystemAssociation> associations, Long codeSystemEntityVersionId) {
    SqlBuilder sb = new SqlBuilder("update code_system_association set sys_status = 'C'");
    sb.append(" where source_code_system_entity_version_id = ? and sys_status = 'A'", codeSystemEntityVersionId);
    sb.andNotIn("id", associations, CodeSystemAssociation::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void batchUpsert(List<CodeSystemAssociation> associations, Long codeSystemEntityVersionId) {
    if (associations == null) {
      return;
    }
    associations.forEach(a -> {
      SqlBuilder sb =
          upsert(new SqlBuilder("insert into code_system_association (" +
                  "id, " +
                  "source_code_system_entity_version_id, " +
                  "target_code_system_entity_version_id, " +
                  "code_system, " +
                  "association_type, " +
                  "status) select ?,?,?,?,?,?",
                  a.getId(),
                  codeSystemEntityVersionId,
                  a.getTargetId(),
                  a.getCodeSystem(),
                  a.getAssociationType(),
                  a.getStatus()
              ),
              new SqlBuilder(
                  "UPDATE code_system_association SET " +
                      "target_code_system_entity_version_id = ?, " +
                      "association_type = ?, " +
                      "status = ? where code_system = ? and source_code_system_entity_version_id = ? and id = ? and sys_status = 'A'",
                  a.getTargetId(),
                  a.getAssociationType(),
                  a.getStatus(),
                  a.getCodeSystem(),
                  codeSystemEntityVersionId,
                  a.getId()));
      jdbcTemplate.update(sb.getSql(), sb.getParams());
    });
  }
}
