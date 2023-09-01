package com.kodality.termx.terminology.mapset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
import com.kodality.termx.ts.mapset.MapSetQueryParams.Ordering;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class MapSetRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MapSet.class, bp -> {
    bp.addColumnProcessor("title", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("description", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("purpose", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("settings", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("copyright", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("identifiers", PgBeanProcessor.fromJson(JsonUtil.getListType(Identifier.class)));
    bp.addColumnProcessor("contacts", PgBeanProcessor.fromJson(JsonUtil.getListType(ContactDetail.class)));
  });

  public void save(MapSet mapSet) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", mapSet.getId());
    ssb.property("uri", mapSet.getUri());
    ssb.property("publisher", mapSet.getPublisher());
    ssb.property("name", mapSet.getName());
    ssb.jsonProperty("title", mapSet.getTitle());
    ssb.jsonProperty("description", mapSet.getDescription());
    ssb.jsonProperty("purpose", mapSet.getPurpose());
    ssb.property("narrative", mapSet.getNarrative());
    ssb.property("experimental", mapSet.getExperimental());
    ssb.jsonProperty("identifiers", mapSet.getIdentifiers());
    ssb.jsonProperty("contacts", mapSet.getContacts());
    ssb.jsonProperty("copyright", mapSet.getCopyright());
    ssb.jsonProperty("settings", mapSet.getSettings());
    ssb.property("sys_status", "A");

    SqlBuilder sb = ssb.buildUpsert("terminology.map_set", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public MapSet load(String id) {
    String sql = "select * from terminology.map_set where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<MapSet> query(MapSetQueryParams params) {
    String join = "left join terminology.map_set_version msv on msv.map_set = ms.id and msv.sys_status = 'A' " +
        "left join terminology.map_set_association msa on msa.map_set = ms.id and msa.sys_status = 'A' " +
        "left join sys.package_version_resource pvr on pvr.resource_type = 'map-set' and pvr.resource_id = ms.id and pvr.sys_status = 'A' " +
        "left join sys.package_version pv on pv.id = pvr.version_id and pv.sys_status = 'A' " +
        "left join sys.package p on p.id = pv.package_id and p.sys_status = 'A' " +
        "left join sys.space s on s.id = p.space_id and s.sys_status = 'A' ";
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(distinct(ms.id)) from terminology.map_set ms " + join);
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select distinct on (ms.id) ms.* from terminology.map_set ms " + join);
      sb.append(filter(params));
      sb.append(order(params, sortMap(params.getLang())));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(MapSetQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("where ms.sys_status = 'A'");
    sb.appendIfNotNull("and ms.id = ?", params.getId());
    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("ms.id", p));
    sb.appendIfNotNull("and ms.id ~* ?", params.getIdContains());
    if (CollectionUtils.isNotEmpty(params.getPermittedIds())) {
      sb.and().in("ms.id", params.getPermittedIds());
    }
    sb.appendIfNotNull("and ms.uri = ?", params.getUri());
    sb.appendIfNotNull("and ms.uri ~* ?", params.getUriContains());
    sb.appendIfNotNull("and ms.description = ?", params.getDescription());
    sb.appendIfNotNull("and ms.description ~* ?", params.getDescriptionContains());
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(ms.names) where value = ?)", params.getName());
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(ms.names) where value ~* ?)", params.getNameContains());
    sb.appendIfNotNull("and ms.source_value_set = ?", params.getSourceValueSet());
    sb.appendIfNotNull("and ms.target_value_set = ?", params.getTargetValueSet());
    if (StringUtils.isNotEmpty(params.getText())) {
      sb.append("and (ms.id = ? or ms.uri = ? or ms.description = ? or exists (select 1 from jsonb_each_text(ms.names) where value = ?))",
          params.getText(), params.getText(), params.getText(), params.getText());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (ms.id ~* ? or ms.uri ~* ? or ms.description ~* ? or exists (select 1 from jsonb_each_text(ms.names) where value ~* ?))",
          params.getTextContains(), params.getTextContains(), params.getTextContains(), params.getTextContains());
    }
    sb.appendIfNotNull("and msv.version = ?", params.getVersionVersion());
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
      sortMap.put(Ordering.name, "ms.names ->> '" + lang + "'");
    }
    return sortMap;
  }

  public void cancel(String mapSet) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.cancel_map_set(?)", mapSet);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }
}
