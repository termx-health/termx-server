package com.kodality.termx.terminology.terminology.mapset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.OtherTitle;
import com.kodality.termx.ts.UseContext;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetProperty;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
import com.kodality.termx.ts.mapset.MapSetQueryParams.Ordering;
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
    bp.addColumnProcessor("use_context", PgBeanProcessor.fromJson(JsonUtil.getListType(UseContext.class)));
    bp.addColumnProcessor("other_title", PgBeanProcessor.fromJson(JsonUtil.getListType(OtherTitle.class)));
    bp.addColumnProcessor("identifiers", PgBeanProcessor.fromJson(JsonUtil.getListType(Identifier.class)));
    bp.addColumnProcessor("contacts", PgBeanProcessor.fromJson(JsonUtil.getListType(ContactDetail.class)));
    bp.addColumnProcessor("properties", PgBeanProcessor.fromJson(JsonUtil.getListType(MapSetProperty.class)));
  });

  private static final String select = "select distinct on (ms.id) ms.*, " +
      "(select jsonb_agg(msp.p) from (select json_build_object(" +
      "               'id', msp.id, " +
      "               'name', msp.name, " +
      "               'uri', msp.uri, " +
      "               'type', msp.type, " +
      "               'description', msp.description, " +
      "               'status', msp.status, " +
      "               'orderNumber', msp.order_number, " +
      "               'required', msp.required, " +
      "               'rule', msp.rule, " +
      "               'created', msp.created, " +
      "               'definedEntityPropertyId', msp.defined_entity_property_id) as p " +
      "from terminology.map_set_property msp where msp.map_set = ms.id and msp.sys_status = 'A' order by msp.order_number) msp) as properties ";

  public void save(MapSet mapSet) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", mapSet.getId());
    ssb.property("uri", mapSet.getUri());
    ssb.property("publisher", mapSet.getPublisher());
    ssb.property("name", mapSet.getName());
    ssb.jsonProperty("other_title", mapSet.getOtherTitle());
    ssb.jsonProperty("title", mapSet.getTitle());
    ssb.jsonProperty("description", mapSet.getDescription());
    ssb.jsonProperty("purpose", mapSet.getPurpose());
    ssb.jsonProperty("topic", mapSet.getTopic());
    ssb.jsonProperty("use_context", mapSet.getUseContext());
    ssb.property("narrative", mapSet.getNarrative());
    ssb.property("experimental", mapSet.getExperimental());
    ssb.property("source_reference", mapSet.getSourceReference());
    ssb.property("replaces", mapSet.getReplaces());
    ssb.jsonProperty("identifiers", mapSet.getIdentifiers());
    ssb.jsonProperty("configuration_attributes", mapSet.getConfigurationAttributes());
    ssb.jsonProperty("contacts", mapSet.getContacts());
    ssb.jsonProperty("copyright", mapSet.getCopyright());
    ssb.jsonProperty("settings", mapSet.getSettings());
    ssb.property("external_web_source", mapSet.isExternalWebSource());
    ssb.property("sys_status", "A");

    SqlBuilder sb = ssb.buildUpsert("terminology.map_set", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public MapSet load(String id) {
    String sql = select + "from terminology.map_set ms where ms.sys_status = 'A' and ms.id = ?";
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
      SqlBuilder sb = new SqlBuilder(select + "from terminology.map_set ms " + join);
      sb.append(filter(params));
      sb.append(order(params, sortMap(params.getLang())));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(MapSetQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where ms.sys_status = 'A'");
    sb.and().in("ms.id", params.getPermittedIds());

    // id
    sb.appendIfNotNull("and ms.id = ?", params.getId());
    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("ms.id", p));
    sb.appendIfNotNull("and ms.id ~* ?", params.getIdContains());

    // uri
    sb.appendIfNotNull("and ms.uri = ?", params.getUri());
    sb.appendIfNotNull("and ms.uri ~* ?", params.getUriContains());

    // identifier
    if (StringUtils.isNotEmpty(params.getIdentifier())) {
      String[] tokens = PipeUtil.parsePipe(params.getIdentifier());
      sb.and("exists (select 1 from jsonb_array_elements(ms.identifiers) i where (i ->> 'system') = coalesce(?, (i ->> 'system')) and (i ->> 'value') = ?)",
          tokens[0], tokens[1]);
    }

    // description
    sb.appendIfNotNull("and terminology.jsonb_search(ms.description) like '%`' || terminology.search_translate(?) || '`%'", params.getDescription());
    sb.appendIfNotNull("and terminology.jsonb_search(ms.description) like '%`' || terminology.search_translate(?) || '%`%'", params.getDescriptionStarts());
    sb.appendIfNotNull("and terminology.jsonb_search(ms.description) like '%' || terminology.search_translate(?) || '%'", params.getDescriptionContains());

    // name
    sb.appendIfNotNull("and ms.name = ?", params.getName());
    sb.appendIfNotNull("and ms.name ilike ? || '%'", params.getNameStarts());
    sb.appendIfNotNull("and ms.name ~* ?", params.getNameContains());

    // title
    sb.appendIfNotNull("and terminology.jsonb_search(ms.title) like '%`' || terminology.search_translate(?) || '`%'", params.getTitle());
    sb.appendIfNotNull("and terminology.jsonb_search(ms.title) like '%`' || terminology.search_translate(?) || '%`%'", params.getTitleStarts());
    sb.appendIfNotNull("and terminology.jsonb_search(ms.title) like '%' || terminology.search_translate(?) || '%'", params.getTitleContains());

    // publisher
    sb.appendIfNotNull("and ms.publisher = ?", params.getPublisher());
    sb.appendIfNotNull("and ms.publisher ilike ? || '%'", params.getPublisherStarts());
    sb.appendIfNotNull("and ms.publisher ~* ?", params.getPublisherContains());

    // text
    if (StringUtils.isNotEmpty(params.getText())) {
      sb.append("and ( terminology.text_search(ms.id, ms.uri, ms.name) like '%`' || terminology.search_translate(?) || '`%'" +
              "     or terminology.jsonb_search(ms.title) like '%`' || terminology.search_translate(?) || '`%' )",
          params.getText(), params.getText());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and ( terminology.text_search(ms.id, ms.uri, ms.name) like '%' || terminology.search_translate(?) || '%'" +
              "     or terminology.jsonb_search(ms.title) like '%' || terminology.search_translate(?) || '%' )",
          params.getTextContains(), params.getTextContains());
    }

    sb.appendIfNotNull("and msv.version = ?", params.getVersionVersion());
    sb.appendIfNotNull("and msv.status = ?", params.getVersionStatus());
    sb.appendIfNotNull("and pv.id = ?", params.getPackageVersionId());
    sb.appendIfNotNull("and msa.source_code = ?", params.getVersionConceptSourceCode());
    sb.appendIfNotNull("and msa.target_code = ?", params.getVersionConceptTargetCode());
    sb.appendIfNotNull("and msv.release_date = ?", params.getVersionReleaseDate());
    sb.appendIfNotNull("and msv.release_date >= ?", params.getVersionReleaseDateGe());

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

  public void changeId(String currentId, String newId) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.change_map_set_id(?,?)", currentId, newId);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }
}
