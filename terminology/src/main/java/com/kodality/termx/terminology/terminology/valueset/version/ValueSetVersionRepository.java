package com.kodality.termx.terminology.terminology.valueset.version;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.db.util.PgUtil;
import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;

@Singleton
public class ValueSetVersionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetVersion.class, bp -> {
    bp.addColumnProcessor("supported_languages", PgBeanProcessor.fromArray());
    bp.addColumnProcessor("rule_set", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("description", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("snapshot", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("identifiers", PgBeanProcessor.fromJson(JsonUtil.getListType(Identifier.class)));
  });

  private final static String select = "select distinct on (vsv.id) vsv.*, " +
      "(select json_build_object(" +
      "   'id', vss.id, " +
      "   'valueSet', vss.value_set, " +
      "   'valueSetVersion', (select json_build_object('id', vsv2.id, 'version', vsv2.version) from terminology.value_set_version vsv2 where vsv2.id = vss.value_set_version_id and vsv2.sys_status = 'A'), " +
      "   'expansion', vss.expansion, " +
      "   'createdAt', vss.created_at, " +
      "   'createdBy', vss.created_by, " +
      "   'conceptsTotal', vss.concepts_total " +
      ") from terminology.value_set_snapshot vss where vsv.id = vss.value_set_version_id and vss.sys_status = 'A') as snapshot ," +
      "(select json_build_object(" +
      "   'id', vsvrs.id, " +
      "   'lockedDate', vsvrs.locked_date, " +
      "   'inactive', vsvrs.inactive, " +
      "   'rules', (select jsonb_agg(json_build_object(" +
      "      'id', vsvr.id, " +
      "      'type', vsvr.type, " +
      "      'properties', vsvr.properties, " +
      "      'concepts', vsvr.concepts, " +
      "      'filters', vsvr.filters, " +
      "      'codeSystem', vsvr.code_system, " +
      "      'codeSystemBaseUri', (select cs.uri from terminology.code_system cs where exists(select 1 from terminology.code_system cs1 where cs1.id = vsvr.code_system and cs1.base_code_system = cs.id and cs1.sys_status = 'A') and cs.sys_status = 'A'), " +
      "      'codeSystemUri', (select cs.uri from terminology.code_system cs where cs.id = vsvr.code_system and cs.sys_status = 'A'), " +
      "      'codeSystemVersion', (select json_build_object('id', csv.id, 'version', csv.version, 'uri', csv.uri, " +
      "                 'baseCodeSystemVersion', (select json_build_object('id', csv1.id, 'version', csv1.version, 'uri', csv1.uri) from terminology.code_system_version csv1 where csv1.id = csv.base_code_system_version_id and csv1.sys_status = 'A')) " +
      "      from terminology.code_system_version csv where csv.id = vsvr.code_system_version_id and csv.sys_status = 'A'), " +
      "      'valueSet', vsvr.value_set, " +
      "      'valueSetUri', (select vs.uri from terminology.value_set vs where vs.id = vsvr.value_set and vs.sys_status = 'A'), " +
      "      'valueSetVersion', (select json_build_object('id', vsv.id, 'version', vsv.version) from terminology.value_set_version vsv where vsv.id = vsvr.value_set_version_id and vsv.sys_status = 'A') " +
      "   )) from terminology.value_set_version_rule vsvr where vsvrs.id = vsvr.rule_set_id and vsvr.sys_status = 'A') " +
      ") from terminology.value_set_version_rule_set vsvrs where vsv.id = vsvrs.value_set_version_id and vsvrs.sys_status = 'A') as rule_set ";

  public void save(ValueSetVersion version) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", version.getId());
    ssb.property("value_set", version.getValueSet());
    ssb.property("version", version.getVersion());
    ssb.property("preferred_language", version.getPreferredLanguage());
    ssb.property("supported_languages", "?::text[]", PgUtil.array(version.getSupportedLanguages()));
    ssb.jsonProperty("description", version.getDescription());
    ssb.property("status", version.getStatus());
    ssb.property("release_date", version.getReleaseDate());
    ssb.property("expiration_date", version.getExpirationDate());
    ssb.property("created", version.getCreated());
    ssb.property("algorithm", version.getAlgorithm());
    ssb.jsonProperty("identifiers", version.getIdentifiers());
    SqlBuilder sb = ssb.buildSave("terminology.value_set_version", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    version.setId(id);
  }

  public ValueSetVersion load(String valueSet, String version) {
    String sql = select + "from terminology.value_set_version vsv where vsv.sys_status = 'A' and vsv.value_set = ? and vsv.version = ?";
    return getBean(sql, bp, valueSet, version);
  }

  public ValueSetVersion load(Long id) {
    String sql = select + "from terminology.value_set_version vsv where vsv.sys_status = 'A' and vsv.id = ?";
    return getBean(sql, bp, id);
  }

  public ValueSetVersion loadLastVersion(String valueSet) {
    String sql = select +
        "from terminology.value_set_version vsv where vsv.sys_status = 'A' and vsv.value_set = ? and (vsv.status = 'active' or vsv.status = 'draft') order by vsv.id, vsv.release_date desc";
    return getBean(sql, bp, valueSet);
  }

  public QueryResult<ValueSetVersion> query(ValueSetVersionQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(distinct(vsv.id)) from terminology.value_set_version vsv " +
          "inner join terminology.value_set vs on vs.id = vsv.value_set and vs.sys_status = 'A' " +
          "left join terminology.value_set_version_rule_set vsvrs on vsvrs.value_set_version_id = vsv.id and vsvrs.sys_status = 'A' " +
          "left join terminology.value_set_version_rule vsvr on vsvr.rule_set_id = vsvrs.id and vsvr.sys_status = 'A' " +
          "left join terminology.code_system cs on cs.id = vsvr.code_system and cs.sys_status = 'A' " +
          "left join terminology.value_set_snapshot vss on vsv.id = vss.value_set_version_id and vss.sys_status = 'A' " +
          "where vsv.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from terminology.value_set_version vsv " +
          "inner join terminology.value_set vs on vs.id = vsv.value_set and vs.sys_status = 'A' " +
          "left join terminology.value_set_version_rule_set vsvrs on vsvrs.value_set_version_id = vsv.id and vsvrs.sys_status = 'A' " +
          "left join terminology.value_set_version_rule vsvr on vsvr.rule_set_id = vsvrs.id and vsvr.sys_status = 'A' " +
          "left join terminology.code_system cs on cs.id = vsvr.code_system and cs.sys_status = 'A' " +
          "left join terminology.value_set_snapshot vss on vsv.id = vss.value_set_version_id and vss.sys_status = 'A' " +
          "where vsv.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ValueSetVersionQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.and().in("vs.id", params.getPermittedValueSets());

    // VS id
    sb.appendIfNotNull("and vsv.value_set = ?", params.getValueSet());

    // VS uri
    sb.appendIfNotNull("and vs.uri = ?", params.getValueSetUri());

    // VS identifier
    if (StringUtils.isNotEmpty(params.getValueSetIdentifier())) {
      String[] tokens = PipeUtil.parsePipe(params.getValueSetIdentifier());
      sb.and("exists (select 1 from jsonb_array_elements(vs.identifiers) i where (i ->> 'system') = coalesce(?, (i ->> 'system')) and (i ->> 'value') = ?)",
          tokens[0], tokens[1]);
    }

    // VS name
    sb.appendIfNotNull("and vs.name = ?", params.getValueSetName());
    sb.appendIfNotNull("and vs.name ilike ? || '%'", params.getValueSetNameStarts());
    sb.appendIfNotNull("and vs.name ~* ?", params.getValueSetNameContains());

    // VS publisher
    sb.appendIfNotNull("and vs.publisher = ?", params.getValueSetPublisher());
    sb.appendIfNotNull("and vs.publisher ilike ? || '%'", params.getValueSetPublisherStarts());
    sb.appendIfNotNull("and vs.publisher ~* ?", params.getValueSetPublisherContains());

    // VS title
    sb.appendIfNotNull("and terminology.jsonb_search(vs.title) like '%`' || terminology.search_translate(?) || '`%'", params.getValueSetTitle());
    sb.appendIfNotNull("and terminology.jsonb_search(vs.title) like '%`' || terminology.search_translate(?) || '%`%'", params.getValueSetTitleStarts());
    sb.appendIfNotNull("and terminology.jsonb_search(vs.title) like '%' || terminology.search_translate(?) || '%'", params.getValueSetTitleContains());

    // VS description
    sb.appendIfNotNull("and terminology.jsonb_search(vs.description) like '%`' || terminology.search_translate(?) || '`%'", params.getValueSetDescription());
    sb.appendIfNotNull("and terminology.jsonb_search(vs.description) like '%`' || terminology.search_translate(?) || '%`%'",
        params.getValueSetDescriptionStarts());
    sb.appendIfNotNull("and terminology.jsonb_search(vs.description) like '%' || terminology.search_translate(?) || '%'",
        params.getValueSetDescriptionContains());


    sb.appendIfNotNull("and exists (select 1 from jsonb_array_elements(vss.expansion::jsonb) exp where (exp -> 'concept' ->> 'code') = ?)",
        params.getConceptCode());
    sb.appendIfNotNull("and vsvr.type = 'include' and cs.uri = ?", params.getCodeSystemUri());

    sb.and().in("vsv.id", params.getIds(), Long::valueOf);
    sb.appendIfNotNull("and vsv.version = ?", params.getVersion());
    sb.appendIfNotNull("and vsv.status = ?", params.getStatus());
    sb.appendIfNotNull("and (vsv.release_date is null or vsv.release_date = ?)", params.getReleaseDate());
    sb.appendIfNotNull("and (vsv.release_date is null or vsv.release_date >= ?)", params.getReleaseDateGe());
    sb.appendIfNotNull("and (vsv.release_date is null or vsv.release_date <= ?)", params.getReleaseDateLe());
    sb.appendIfNotNull("and (vsv.expiration_date is null or vsv.expiration_date >= ?)", params.getExpirationDateGe());
    return sb;
  }

  public void saveStatus(String valueSet, String version, String status) {
    String sql = "UPDATE terminology.value_set_version SET status = ? WHERE value_set = ? AND version = ? AND sys_status = 'A' AND status <> ?";
    jdbcTemplate.update(sql, status, valueSet, version, status);
  }

  public void saveExpirationDate(ValueSetVersion version) {
    String sql = "UPDATE terminology.value_set_version SET expiration_date = ? WHERE id = ?";
    jdbcTemplate.update(sql, version.getExpirationDate(), version.getId());
  }

  public ValueSetVersion loadLastVersionByUri(String uri) {
    String sql = select + "from terminology.value_set_version vsv where vsv.sys_status = 'A' and (vsv.status = 'active' or vsv.status = 'draft') and " +
        "exists (select 1 from terminology.value_set vs where vs.id = vsv.value_set and vs.uri = ? and vs.sys_status = 'A') " +
        "order by vsv.id, vsv.release_date desc";
    return getBean(sql, bp, uri);
  }

  public void cancel(Long id) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.cancel_value_set_version(?)", id);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }

  public ValueSetVersion loadPreviousVersion(String valueSet, String version) {
    String sql = "with current_version as (select release_date from terminology.value_set_version where value_set = ? and version = ? and sys_status = 'A') " +
            select + "from terminology.value_set_version vsv where vsv.value_set = ? and vsv.release_date < (select release_date from current_version) and vsv.sys_status = 'A' " +
            "order by vsv.id, vsv.release_date desc";
    return getBean(sql, bp, valueSet, version, valueSet);
  }
}
