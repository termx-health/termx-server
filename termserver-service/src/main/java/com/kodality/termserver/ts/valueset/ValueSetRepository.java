package com.kodality.termserver.ts.valueset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ts.ContactDetail;
import com.kodality.termserver.ts.valueset.ValueSetQueryParams.Ordering;
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
    String join = "left join terminology.value_set_version vsv on vsv.value_set = vs.id and vsv.sys_status = 'A' " +
        "left join terminology.value_set_version_rule_set vsvrs on vsvrs.value_set_version_id = vsv.id and vsvrs.sys_status = 'A' " +
        "left join terminology.value_set_version_rule vsvr on vsvr.rule_set_id = vsvrs.id and vsvr.sys_status = 'A' " +
        "left join terminology.code_system cs on cs.id = vsvr.code_system and cs.sys_status = 'A' " +
        "left join terminology.package_version_resource pvr on pvr.resource_type = 'value-set' and pvr.resource_id = vs.id and pvr.sys_status = 'A' " +
        "left join terminology.package_version pv on pv.id = pvr.version_id and pv.sys_status = 'A' " +
        "left join terminology.package p on p.id = pv.package_id and p.sys_status = 'A' " +
        "left join terminology.project pr on pr.id = p.project_id and pr.sys_status = 'A' ";
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
    sb.appendIfNotNull("and vs.description = ?", params.getDescription());
    sb.appendIfNotNull("and vs.description ~* ?", params.getDescriptionContains());
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(vs.names) where value = ?)", params.getName());
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(vs.names) where value ~* ?)", params.getNameContains());
    if (StringUtils.isNotEmpty(params.getText())) {
      sb.append("and (vs.id = ? or vs.uri = ? or vs.description = ? or exists (select 1 from jsonb_each_text(vs.names) where value = ?))",
          params.getText(), params.getText(), params.getText(), params.getText());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (vs.id ~* ? or vs.uri ~* ? or vs.description ~* ? or exists (select 1 from jsonb_each_text(vs.names) where value ~* ?))",
          params.getTextContains(), params.getTextContains(), params.getTextContains(), params.getTextContains());
    }
    sb.appendIfNotNull("and vsv.id = ?", params.getVersionId());
    sb.appendIfNotNull("and vsv.version = ?", params.getVersionVersion());
    sb.appendIfNotNull("and vsv.source = ?", params.getVersionSource());
    sb.appendIfNotNull("and vsv.status = ?", params.getVersionStatus());
    sb.appendIfNotNull("and vsvr.type = 'include' and vsvr.code_system = ?", params.getCodeSystem());
    sb.appendIfNotNull("and vsvr.type = 'include' and cs.uri = ?", params.getCodeSystemUri());
    sb.appendIfNotNull("and vsv.id is not null and exists (select 1 from terminology.value_set_expand(vsv.id) vse where (vse.concept ->> 'code') = ?)", params.getConceptCode());
    sb.appendIfNotNull("and pv.id = ?", params.getPackageVersionId());
    sb.appendIfNotNull("and p.id = ?", params.getPackageId());
    sb.appendIfNotNull("and pr.id = ?", params.getProjectId());
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
