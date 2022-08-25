package com.kodality.termserver.ts.codesystem.association;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemAssociationQueryParams;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class CodeSystemAssociationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemAssociation.class, bp -> {
    bp.overrideColumnMapping("target_code_system_entity_version_id", "targetId");
    bp.overrideColumnMapping("source_code_system_entity_version_id", "sourceId");
  });

  public void save(CodeSystemAssociation association, Long codeSystemEntityVersionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", association.getId());
    ssb.property("source_code_system_entity_version_id", codeSystemEntityVersionId);
    ssb.property("target_code_system_entity_version_id", association.getTargetId());
    ssb.property("code_system", association.getCodeSystem());
    ssb.property("association_type", association.getAssociationType());
    ssb.property("status", association.getStatus());

    SqlBuilder sb = ssb.buildUpsert("terminology.code_system_association", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
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

  public QueryResult<CodeSystemAssociation> query(CodeSystemAssociationQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.code_system_association where sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.code_system_association where sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(CodeSystemAssociationQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getCodeSystemEntityVersionId())) {
      sb.and().in("source_code_system_entity_version_id", params.getCodeSystemEntityVersionId(), Long::valueOf);
    }
    return sb;
  }

}
