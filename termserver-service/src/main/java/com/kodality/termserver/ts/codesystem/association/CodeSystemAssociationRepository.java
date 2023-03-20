package com.kodality.termserver.ts.codesystem.association;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociationQueryParams;
import io.micronaut.core.util.StringUtils;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Singleton
public class CodeSystemAssociationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemAssociation.class, bp -> {
    bp.overrideColumnMapping("target_code_system_entity_version_id", "targetId");
    bp.overrideColumnMapping("source_code_system_entity_version_id", "sourceId");
  });

  private String from = " from terminology.code_system_association csa inner join terminology.code_system_entity_version csev on csev.sys_status = 'A' and csev.id = target_code_system_entity_version_id ";

  public void save(CodeSystemAssociation association, Long codeSystemEntityVersionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", association.getId());
    ssb.property("source_code_system_entity_version_id", codeSystemEntityVersionId);
    ssb.property("target_code_system_entity_version_id", association.getTargetId());
    ssb.property("code_system", association.getCodeSystem());
    ssb.property("association_type", association.getAssociationType());
    ssb.property("status", association.getStatus());
    ssb.property("sys_status", "A");

    SqlBuilder sb = ssb.buildUpsert("terminology.code_system_association", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<CodeSystemAssociation> loadAll(Long codeSystemEntityVersionId) {
    String sql = "select csa.*, csev.code as target_code " + from + "where csa.sys_status = 'A' and csa.source_code_system_entity_version_id = ?";
    return getBeans(sql, bp, codeSystemEntityVersionId);
  }

  public CodeSystemAssociation load(Long id) {
    String sql = "select csa.*, csev.code as target_code " + from + "where csa.sys_status = 'A' and csa.id = ?";
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
      SqlBuilder sb = new SqlBuilder("select count(1)" + from + "where csa.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select csa.*, csev.code as target_code " + from + "where csa.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(CodeSystemAssociationQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getCodeSystemEntityVersionId())) {
      sb.and().in("csa.source_code_system_entity_version_id", params.getCodeSystemEntityVersionId(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getAssociationType())) {
      sb.and().in("csa.association_type", params.getAssociationType());
    }
    return sb;
  }

  public void retain(List<Entry<Long, List<CodeSystemAssociation>>> associations) {
    String query = "update terminology.code_system_association set sys_status = 'C' where source_code_system_entity_version_id = ? and sys_status = 'A' and id not in " +
        "(select jsonb_array_elements(?::jsonb)::bigint)";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setLong(1, associations.get(i).getKey());
        ps.setString(2, JsonUtil.toJson(associations.get(i).getValue().stream().map(CodeSystemAssociation::getId).filter(Objects::nonNull).toList()));
      }
      @Override
      public int getBatchSize() {
        return associations.size();
      }
    });
  }

  public void save(List<Pair<Long, CodeSystemAssociation>> associations) {
    List<Pair<Long, CodeSystemAssociation>> associationsToInsert = associations.stream().filter(p -> p.getValue().getId() == null).toList();
    String query = "insert into terminology.code_system_association (source_code_system_entity_version_id, target_code_system_entity_version_id, code_system, association_type, status) values (?,?,?,?,?)";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        CodeSystemAssociationRepository.this.setValues(ps, i, associationsToInsert);
      }
      @Override public int getBatchSize() {return associationsToInsert.size();}
    });

    List<Pair<Long, CodeSystemAssociation>> associationsToUpdate = associations.stream().filter(p -> p.getValue().getId() != null).toList();
    query = "update terminology.code_system_association SET source_code_system_entity_version_id = ?, target_code_system_entity_version_id = ? , code_system = ?, association_type = ?, status = ? where id = ?";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        CodeSystemAssociationRepository.this.setValues(ps, i, associationsToUpdate);
        ps.setLong(6, associationsToUpdate.get(i).getValue().getId());
      }
      @Override public int getBatchSize() {return associationsToUpdate.size();}
    });
  }

  private void setValues(PreparedStatement ps, int i, List<Pair<Long, CodeSystemAssociation>> associations) throws SQLException {
    ps.setLong(1, associations.get(i).getKey());
    ps.setLong(2, associations.get(i).getValue().getTargetId());
    ps.setString(3, associations.get(i).getValue().getCodeSystem());
    ps.setString(4, associations.get(i).getValue().getAssociationType());
    ps.setString(5, associations.get(i).getValue().getStatus());
  }

}
