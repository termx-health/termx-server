package com.kodality.termserver.ts.codesystem.association;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class CodeSystemAssociationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemAssociation.class, bp -> {
    bp.overrideColumnMapping("target_code_system_entity_version_id", "targetId");
  });

  public void save(CodeSystemAssociation association, Long codeSystemEntityVersionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", association.getId());
    ssb.property("source_code_system_entity_version_id", codeSystemEntityVersionId);
    ssb.property("target_code_system_entity_version_id", association.getTargetId());
    ssb.property("code_system", association.getCodeSystem());
    ssb.property("association_type", association.getAssociationType());
    ssb.property("status", association.getStatus());

    SqlBuilder sb = ssb.buildSave("terminology.code_system_association", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    association.setId(id);
  }

  public List<CodeSystemAssociation> loadAll(Long codeSystemEntityVersionId) {
    String sql = "select * from terminology.code_system_association where sys_status = 'A' and source_code_system_entity_version_id = ?";
    return getBeans(sql, bp, codeSystemEntityVersionId);
  }

  public CodeSystemAssociation load(Long id) {
    String sql = "select * from terminology.code_system_association where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public void retain(List<CodeSystemAssociation> associations, Long codeSystemEntityVersionId) {
    SqlBuilder sb = new SqlBuilder("update terminology.code_system_association set sys_status = 'C'");
    sb.append(" where source_code_system_entity_version_id = ? and sys_status = 'A'", codeSystemEntityVersionId);
    sb.andNotIn("id", associations, CodeSystemAssociation::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void delete(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.code_system_association set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
