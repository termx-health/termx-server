package com.kodality.termx.terminology.codesystem.designation;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.DesignationQueryParams;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Singleton
public class DesignationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Designation.class);

  String from = " from terminology.designation d " +
      "inner join terminology.entity_property ep on ep.id = d.designation_type_id " +
      "left join terminology.code_system_supplement css on css.target_id = d.id and css.target_type = 'Designation' and css.sys_status = 'A'";

  public void save(Designation designation, Long codeSystemEntityVersionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", designation.getId());
    ssb.property("code_system_entity_version_id", codeSystemEntityVersionId);
    ssb.property("designation_type_id", designation.getDesignationTypeId());
    ssb.property("name", designation.getName());
    ssb.property("language", designation.getLanguage());
    ssb.property("rendering", designation.getRendering());
    ssb.property("preferred", designation.isPreferred());
    ssb.property("case_significance", designation.getCaseSignificance());
    ssb.property("designation_kind", designation.getDesignationKind());
    ssb.property("description", designation.getDescription());
    ssb.property("status", designation.getStatus());

    SqlBuilder sb = ssb.buildSave("terminology.designation", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    designation.setId(id);
  }

  public Designation load(Long id) {
    String sql = "select d.*, ep.name as designation_type, css.id supplement_id" + from + "where d.sys_status = 'A' and d.id = ?";
    return getBean(sql, bp, id);
  }

  public List<Designation> loadAll(Long codeSystemEntityVersionId) {
    String sql = "select d.*, ep.name as designation_type, css.id supplement_id" + from + "where d.sys_status = 'A' and d.code_system_entity_version_id = ?";
    return getBeans(sql, bp, codeSystemEntityVersionId);
  }

  public QueryResult<Designation> query(DesignationQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1)" + from + "where d.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select d.*, ep.name as designation_type, css.id supplement_id" + from + "where d.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(DesignationQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getId())) {
      sb.and().in("d.id", params.getId(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getCodeSystemEntityVersionId())) {
      sb.and().in("d.code_system_entity_version_id", params.getCodeSystemEntityVersionId(), Long::valueOf);
    }
    sb.appendIfNotNull("and d.name = ?", params.getName());
    sb.appendIfNotNull("and d.language = ?", params.getLanguage());
    sb.appendIfNotNull("and d.designation_kind = ?", params.getDesignationKind());
    sb.appendIfNotNull("and d.designation_type_id = ?", params.getDesignationTypeId());

    if (params.getConceptCode() != null || params.getConceptId() != null || params.getCodeSystem() != null) {
      sb.append("and exists( select 1 from terminology.concept c " +
          "inner join terminology.code_system_entity_version csev on csev.code_system_entity_id = c.id and csev.sys_status = 'A' " +
          "where c.sys_status = 'A' and csev.id = d.code_system_entity_version_id");
      sb.appendIfNotNull("and c.code = ?", params.getConceptCode());
      sb.appendIfNotNull("and c.id = ?", params.getConceptId());
      sb.appendIfNotNull("and c.code_system = ?", params.getCodeSystem());
      sb.append(")");
    }
    return sb;
  }

  public void retain(List<Designation> designations, Long codeSystemEntityVersionId) {
    SqlBuilder sb = new SqlBuilder("update terminology.designation set sys_status = 'C'");
    sb.append(" where code_system_entity_version_id = ? and sys_status = 'A'", codeSystemEntityVersionId);
    sb.andNotIn("id", designations, Designation::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void delete(Long propertyId) {
    SqlBuilder sb = new SqlBuilder("update terminology.designation set sys_status = 'C' where designation_type_id = ? and sys_status = 'A'", propertyId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void retain(List<Entry<Long, List<Designation>>> designations) {
    String query = "update terminology.designation set sys_status = 'C' where code_system_entity_version_id = ? and sys_status = 'A' and id not in " +
        "(select jsonb_array_elements(?::jsonb)::bigint)";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        ps.setLong(1, designations.get(i).getKey());
        ps.setString(2, JsonUtil.toJson(designations.get(i).getValue().stream().map(Designation::getId).filter(Objects::nonNull).toList()));
      }
      @Override
      public int getBatchSize() {
        return designations.size();
      }
    });
  }

  public void save(List<Pair<Long, Designation>> designations) {
    List<Pair<Long, Designation>> designationsToInsert = designations.stream().filter(p -> p.getValue().getId() == null).toList();
    String query = "insert into terminology.designation (code_system_entity_version_id, designation_type_id, name, language, rendering, preferred, case_significance, designation_kind, description, status) values (?,?,?,?,?,?,?,?,?,?)";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        DesignationRepository.this.setValues(ps, i, designationsToInsert);
      }
      @Override public int getBatchSize() {return designationsToInsert.size();}
    });

    List<Pair<Long, Designation>> designationsToUpdate = designations.stream().filter(p -> p.getValue().getId() != null).toList();
    query = "update terminology.designation SET code_system_entity_version_id = ?, designation_type_id = ? , name = ?, language = ?, rendering = ?, preferred = ?, case_significance = ?, designation_kind = ?, description = ?, status = ? where id = ?";
    jdbcTemplate.batchUpdate(query, new BatchPreparedStatementSetter() {
      @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
        DesignationRepository.this.setValues(ps, i, designationsToUpdate);
        ps.setLong(12, designationsToUpdate.get(i).getValue().getId());
      }
      @Override public int getBatchSize() {return designationsToUpdate.size();}
    });
  }

  private void setValues(PreparedStatement ps, int i, List<Pair<Long, Designation>> designations) throws SQLException {
    ps.setLong(1, designations.get(i).getKey());
    ps.setLong(2, designations.get(i).getValue().getDesignationTypeId());
    ps.setString(3, designations.get(i).getValue().getName());
    ps.setString(4, designations.get(i).getValue().getLanguage());
    ps.setString(5, designations.get(i).getValue().getRendering());
    ps.setBoolean(6, designations.get(i).getValue().isPreferred());
    ps.setString(7, designations.get(i).getValue().getCaseSignificance());
    ps.setString(8, designations.get(i).getValue().getDesignationKind());
    ps.setString(9, designations.get(i).getValue().getDescription());
    ps.setString(10, designations.get(i).getValue().getStatus());
  }
}
