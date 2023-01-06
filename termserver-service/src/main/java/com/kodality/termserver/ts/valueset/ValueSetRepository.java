package com.kodality.termserver.ts.valueset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ContactDetail;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import com.kodality.termserver.valueset.ValueSetQueryParams.Ordering;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class ValueSetRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSet.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("contacts", PgBeanProcessor.fromJson(JsonUtil.getListType(ContactDetail.class)));
  });

  public void save(ValueSet valueSet) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", valueSet.getId());
    ssb.property("uri", valueSet.getUri());
    ssb.jsonProperty("names", valueSet.getNames());
    ssb.jsonProperty("contacts", valueSet.getContacts());
    ssb.property("narrative", valueSet.getNarrative());
    ssb.property("description", valueSet.getDescription());
    ssb.property("sys_status", "A");

    SqlBuilder sb = ssb.buildUpsert("terminology.value_set", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public ValueSet load(String id) {
    String sql = "select * from terminology.value_set where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<ValueSet> query(ValueSetQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.value_set vs where vs.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select vs.* from terminology.value_set vs where vs.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(order(params, sortMap(params.getLang())));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ValueSetQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and id = ?", params.getId());
    sb.appendIfNotNull("and id ~* ?", params.getIdContains());
    if (CollectionUtils.isNotEmpty(params.getPermittedIds())) {
      sb.and().in("id", params.getPermittedIds());
    }
    sb.appendIfNotNull("and uri = ?", params.getUri());
    sb.appendIfNotNull("and uri ~* ?", params.getUriContains());
    sb.appendIfNotNull("and description = ?", params.getDescription());
    sb.appendIfNotNull("and description ~* ?", params.getDescriptionContains());
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(vs.names) where value = ?)", params.getName());
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(vs.names) where value ~* ?)", params.getNameContains());

    if (StringUtils.isNotEmpty(params.getText())) {
      sb.append("and (id = ? or uri = ? or description = ? or exists (select 1 from jsonb_each_text(vs.names) where value = ?))", params.getText(), params.getText(), params.getText(), params.getText());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (id ~* ? or uri ~* ? or description ~* ? or exists (select 1 from jsonb_each_text(vs.names) where value ~* ?))", params.getTextContains(), params.getTextContains(), params.getTextContains(), params.getTextContains());
    }

    if (params.getVersionId() != null || params.getVersionVersion() != null || params.getVersionSource() != null || params.getVersionStatus() != null) {
      sb.append("and exists (select 1 from terminology.value_set_version vsv where vsv.value_set = vs.id and vsv.sys_status = 'A'");
      sb.appendIfNotNull("and vsv.id = ?", params.getVersionId());
      sb.appendIfNotNull("and vsv.version = ?", params.getVersionVersion());
      sb.appendIfNotNull("and vsv.source = ?", params.getVersionSource());
      sb.appendIfNotNull("and vsv.status = ?", params.getVersionStatus());
      sb.append(")");
    }

    if (StringUtils.isNotEmpty(params.getCodeSystem()) || StringUtils.isNotEmpty(params.getConceptCode()) || StringUtils.isNotEmpty(params.getCodeSystemUri())) {
      sb.append("and exists(select 1 from terminology.value_set_version_rule vsvr " +
          "inner join terminology.value_set_version_rule_set vsvrs on vsvrs.id = vsvr.rule_set_id and vsvrs.sys_status = 'A' " +
          "inner join terminology.value_set_version vsv on vsv.id = vsvrs.value_set_version_id and vsv.sys_status = 'A' " +
          "where vsv.value_set = vs.id and vsvr.sys_status = 'A' and vsvr.type = 'include'");
      sb.appendIfNotNull("and vsvr.code_system = ?", params.getCodeSystem());
      sb.appendIfNotNull("and exists (select 1 from terminology.code_system cs where cs.id = vsvr.code_system and cs.uri = ?)", params.getCodeSystemUri());
      sb.appendIfNotNull("and exists (select 1 from terminology.value_set_expand(vsv.id) vse where (vse.concept ->> 'code') = ?)", params.getConceptCode());
      sb.append(")");
    }
    return sb;
  }

  private Map<String, String> sortMap(String lang) {
    Map<String, String> sortMap = new HashMap<>(Map.of(
        Ordering.id, "id",
        Ordering.uri, "uri",
        Ordering.description, "description"
    ));
    if (StringUtils.isNotEmpty(lang)) {
      sortMap.put(Ordering.name, "vs.names ->> '" + lang + "'");
    }
    return sortMap;
  }

  public void cancel(String valueSet) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.cancel_value_set(?)", valueSet);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }
}
