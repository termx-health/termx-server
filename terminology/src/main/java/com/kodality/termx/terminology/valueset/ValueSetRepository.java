package com.kodality.termx.terminology.valueset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetQueryParams.Ordering;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class ValueSetRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSet.class, bp -> {
    bp.addColumnProcessor("title", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("description", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("purpose", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("settings", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("copyright", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("contacts", PgBeanProcessor.fromJson(JsonUtil.getListType(ContactDetail.class)));
    bp.addColumnProcessor("identifiers", PgBeanProcessor.fromJson(JsonUtil.getListType(Identifier.class)));
  });

  public void save(ValueSet valueSet) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", valueSet.getId());
    ssb.property("uri", valueSet.getUri());
    ssb.property("publisher", valueSet.getPublisher());
    ssb.property("name", valueSet.getName());
    ssb.jsonProperty("title", valueSet.getTitle());
    ssb.jsonProperty("description", valueSet.getDescription());
    ssb.jsonProperty("purpose", valueSet.getPurpose());
    ssb.jsonProperty("identifiers", valueSet.getIdentifiers());
    ssb.jsonProperty("contacts", valueSet.getContacts());
    ssb.jsonProperty("copyright", valueSet.getCopyright());
    ssb.property("narrative", valueSet.getNarrative());
    ssb.property("experimental", valueSet.getExperimental());
    ssb.jsonProperty("settings", valueSet.getSettings());
    ssb.property("sys_status", "A");

    SqlBuilder sb = ssb.buildUpsert("terminology.value_set", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public ValueSet load(String id) {
    String sql = "select * from terminology.value_set where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<ValueSet> query(ValueSetQueryParams params) {
    String join = "left join terminology.value_set_version vsv on vsv.value_set = vs.id and vsv.sys_status = 'A' " +
        "left join terminology.value_set_version_rule_set vsvrs on vsvrs.value_set_version_id = vsv.id and vsvrs.sys_status = 'A' " +
        "left join terminology.value_set_version_rule vsvr on vsvr.rule_set_id = vsvrs.id and vsvr.sys_status = 'A' " +
        "left join terminology.code_system cs on cs.id = vsvr.code_system and cs.sys_status = 'A' " +
        "left join terminology.value_set_snapshot vss on vsv.id = vss.value_set_version_id and vss.sys_status = 'A' " +
        "left join sys.package_version_resource pvr on pvr.resource_type = 'value-set' and pvr.resource_id = vs.id and pvr.sys_status = 'A' " +
        "left join sys.package_version pv on pv.id = pvr.version_id and pv.sys_status = 'A' " +
        "left join sys.package p on p.id = pv.package_id and p.sys_status = 'A' " +
        "left join sys.space s on s.id = p.space_id and s.sys_status = 'A' ";
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(distinct(vs.id)) from terminology.value_set vs " + join);
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select distinct on (vs.id) vs.* from terminology.value_set vs " + join);
      sb.append(filter(params));
      sb.append(order(params, sortMap(params.getLang())));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ValueSetQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("where vs.sys_status = 'A'");
    sb.appendIfNotNull("and vs.id = ?", params.getId());
    sb.appendIfNotNull("and vs.id ~* ?", params.getIdContains());
    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("vs.id", p));
    if (CollectionUtils.isNotEmpty(params.getPermittedIds())) {
      sb.and().in("vs.id", params.getPermittedIds());
    }
    sb.appendIfNotNull("and vs.uri = ?", params.getUri());
    sb.appendIfNotNull("and vs.uri ~* ?", params.getUriContains());
    sb.appendIfNotNull("and terminology.jsonb_search(vs.description) like '%`' || terminology.search_translate(?) || '`%'", params.getDescription());
    sb.appendIfNotNull("and terminology.jsonb_search(vs.description) like '%' || terminology.search_translate(?) || '%'", params.getDescriptionContains());
    sb.appendIfNotNull("and vs.name = ?", params.getName());
    sb.appendIfNotNull("and vs.name ~* ?", params.getNameContains());
    sb.appendIfNotNull("and terminology.jsonb_search(vs.title) like '%`' || terminology.search_translate(?) || '`%'", params.getTitle());
    sb.appendIfNotNull("and terminology.jsonb_search(vs.title) like '%' || terminology.search_translate(?) || '%'", params.getTitleContains());
    if (StringUtils.isNotEmpty(params.getText())) {
      sb.append("and ( terminology.text_search(vs.id, vs.uri) like '%`' || terminology.search_translate(?) || '`%'" +
              "     or terminology.jsonb_search(vs.title) like '%`' || terminology.search_translate(?) || '`%' )",
          params.getText(), params.getText());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and ( terminology.text_search(vs.id, vs.uri) like '%' || terminology.search_translate(?) || '%'" +
              "     or terminology.jsonb_search(vs.title) like '%' || terminology.search_translate(?) || '%' )",
          params.getTextContains(), params.getTextContains());
    }
    sb.appendIfNotNull("and vsv.id = ?", params.getVersionId());
    sb.appendIfNotNull("and vsv.version = ?", params.getVersionVersion());
    sb.appendIfNotNull("and vsv.source = ?", params.getVersionSource());
    sb.appendIfNotNull("and vsv.status = ?", params.getVersionStatus());
    sb.appendIfNotNull("and vsvr.type = 'include' and vsvr.code_system = ?", params.getCodeSystem());
    sb.appendIfNotNull("and vsvr.type = 'include' and cs.uri = ?", params.getCodeSystemUri());
    if (params.getConceptCode() != null) {
      sb.append("and exists (select 1 from jsonb_array_elements(vss.expansion::jsonb) exp where (exp -> 'concept' ->> 'code') = ?)", params.getConceptCode());
    }
    if (params.getConceptId() != null) {
      sb.append("and exists (select 1 from jsonb_array_elements(vss.expansion::jsonb) exp where (exp -> 'concept' ->> 'id')::bigint = ?)", params.getConceptId());
    }
    sb.appendIfNotNull("and pv.id = ?", params.getPackageVersionId());
    sb.appendIfNotNull("and p.id = ?", params.getPackageId());
    sb.appendIfNotNull("and s.id = ?", params.getSpaceId());
    return sb;
  }

  private Map<String, String> sortMap(String lang) {
    Map<String, String> sortMap = new HashMap<>(Map.of(
        Ordering.id, "id",
        Ordering.uri, "uri",
        Ordering.description, "description"
    ));
    if (StringUtils.isNotEmpty(lang)) {
      sortMap.put(Ordering.name, "vs.title ->> '" + lang + "'");
    }
    return sortMap;
  }

  public void cancel(String valueSet) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.cancel_value_set(?)", valueSet);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }

  public void changeId(String currentId, String newId) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.change_value_set_id(?,?)", currentId, newId);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }
}
