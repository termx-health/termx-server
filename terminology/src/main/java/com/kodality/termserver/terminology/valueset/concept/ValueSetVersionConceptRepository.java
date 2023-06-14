package com.kodality.termserver.terminology.valueset.concept;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
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

  public List<ValueSetVersionConcept> expand(Long valueSetVersionId) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.value_set_expand(?::bigint)", valueSetVersionId);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public List<ValueSetVersionConcept> expand(Long valueSetVersionId, ValueSetVersionRuleSet ruleSet) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.rule_set_expand(?::bigint, ?::jsonb)", valueSetVersionId, JsonUtil.toJson(ruleSet));
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
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system cs where cs.id = vse.concept ->> 'codeSystem' and cs.uri = ?)", params.getCodeSystemUri());
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
