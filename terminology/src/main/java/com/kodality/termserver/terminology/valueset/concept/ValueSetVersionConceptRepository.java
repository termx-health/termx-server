package com.kodality.termserver.terminology.valueset.concept;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionConceptQueryParams;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class ValueSetVersionConceptRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetVersionConcept.class, bp -> {
    bp.addColumnProcessor("concept", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("display", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("additional_designations", PgBeanProcessor.fromJson(JsonUtil.getListType(Designation.class)));
  });

  public void save(ValueSetVersionConcept concept, Long valueSetVersionId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", concept.getId());
    ssb.jsonProperty("concept", concept.getConcept());
    ssb.jsonProperty("display", concept.getDisplay());
    ssb.jsonProperty("additional_designations", concept.getAdditionalDesignations());
    ssb.property("value_set_version_id", valueSetVersionId);
    SqlBuilder sb = ssb.buildSave("terminology.value_set_version_concept", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    concept.setId(id);
  }

  public ValueSetVersionConcept load(Long id) {
    String sql = "select * from terminology.value_set_version_concept where sys_status = 'A' and id = ? ";
    return getBean(sql, bp, id);
  }

  public List<ValueSetVersionConcept> loadAll(Long valueSetVersionId) {
    String sql = "select * from terminology.value_set_version_concept where sys_status = 'A' and value_set_version_id = ? ";
    return getBeans(sql, bp, valueSetVersionId);
  }

  public void retain(List<ValueSetVersionConcept> concepts, Long valueSetVersionId) {
    SqlBuilder sb = new SqlBuilder("update terminology.value_set_version_concept set sys_status = 'C'");
    sb.append(" where value_set_version_id = ? and sys_status = 'A'", valueSetVersionId);
    sb.andNotIn("id", concepts, ValueSetVersionConcept::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void delete(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.value_set_version_concept set sys_status = 'C' where id = ?", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<ValueSetVersionConcept> expand(Long valueSetVersionId) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.value_set_expand(?::bigint)", valueSetVersionId);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public List<ValueSetVersionConcept> expand(ValueSetVersionRuleSet ruleSet) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.rule_set_expand(?::jsonb)", JsonUtil.toJson(ruleSet));
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public QueryResult<ValueSetVersionConcept> query(ValueSetVersionConceptQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.value_set_expand(?::bigint) vse where 1=1", params.getValueSetVersionId());
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.value_set_expand(?::bigint) vse where 1=1", params.getValueSetVersionId());
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ValueSetVersionConceptQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and (vse.concept ->> 'code') = ?", params.getConceptCode());
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system cs where cs.id = vse.concept ->> 'code_system' and cs.uri = ?)", params.getCodeSystemUri());
    sb.appendIfNotNull("and exists (select 1 from code_system cs where cs.sys_status = 'A' and cs.uri = ? and " +
        "exists (select 1 from ))", params.getCodeSystemUri());
    if (params.getCodeSystemVersion() != null) {
      sb.append("and exists (select 1 from terminology.code_system_version csv " +
          "inner join terminology.entity_version_code_system_version_membership evcsvm on evcsvm.code_system_version_id = csv.id and evcsvm.sys_status = 'A' " +
          "inner join terminology.code_system_entity_version csev on csev.id = evcsvm.code_system_entity_version_id and csev.sys_status = 'A' " +
          "where csev.code_system_entity_id = (vse.concept ->> 'id')::bigint and csv.sys_status = 'A'");
      sb.appendIfNotNull("and csv.version = ?", params.getCodeSystemVersion());
      sb.append(")");
    }
    return sb;
  }
}
