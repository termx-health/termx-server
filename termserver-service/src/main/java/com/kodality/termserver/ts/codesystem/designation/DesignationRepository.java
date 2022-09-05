package com.kodality.termserver.ts.codesystem.designation;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.DesignationQueryParams;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class DesignationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Designation.class);

  String from = " from terminology.designation d left join terminology.code_system_supplement css on css.target_id = d.id and css.target_type = 'Designation' ";

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
    String sql = "select d.*, css.id supplement_id" + from + "where d.sys_status = 'A' and d.id = ?";
    return getBean(sql, bp, id);
  }

  public List<Designation> loadAll(Long codeSystemEntityVersionId) {
    String sql = "select d.*, css.id supplement_id" + from + "where d.sys_status = 'A' and d.code_system_entity_version_id = ?";
    return getBeans(sql, bp, codeSystemEntityVersionId);
  }

  public QueryResult<Designation> query(DesignationQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1)" + from + "where d.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select d.*, css.id supplement_id" + from + "where d.sys_status = 'A'");
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
    sb.appendIfNotNull("and exists( select 1 from terminology.concept c " +
        "inner join terminology.code_system_entity_version csev on csev.code_system_entity_id = c.id and csev.sys_status = 'A' " +
        "where c.code = ? and c.sys_status = 'A' and csev.id = d.code_system_entity_version_id)", params.getConceptCode());
    sb.appendIfNotNull("and exists( select 1 from terminology.concept c " +
        "inner join terminology.code_system_entity_version csev on csev.code_system_entity_id = c.id and csev.sys_status = 'A' " +
        "where c.id = ? and c.sys_status = 'A' and csev.id = d.code_system_entity_version_id)", params.getConceptId());
    return sb;
  }

  public void retain(List<Designation> designations, Long codeSystemEntityVersionId) {
    SqlBuilder sb = new SqlBuilder("update terminology.designation set sys_status = 'C'");
    sb.append(" where code_system_entity_version_id = ? and sys_status = 'A'", codeSystemEntityVersionId);
    sb.andNotIn("id", designations, Designation::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void delete(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.designation set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
