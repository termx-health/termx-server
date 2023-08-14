package com.kodality.termx.terminology.codesystem;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams.Ordering;
import com.kodality.termx.ts.codesystem.EntityProperty;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Singleton
public class CodeSystemRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystem.class, bp -> {
    bp.addColumnProcessor("title", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("description", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("purpose", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("settings", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("copyright", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("identifiers", PgBeanProcessor.fromJson(JsonUtil.getListType(Identifier.class)));
    bp.addColumnProcessor("contacts", PgBeanProcessor.fromJson(JsonUtil.getListType(ContactDetail.class)));
    bp.addColumnProcessor("properties", PgBeanProcessor.fromJson(JsonUtil.getListType(EntityProperty.class)));
  });

  private static final String select = "select distinct on (cs.id) cs.*, " +
      "(select jsonb_agg(ep.p) from (select json_build_object('id', ep.id, 'name', ep.name, 'kind', ep.kind, 'type', ep.type, 'description', ep.description, 'status', ep.status, 'orderNumber', ep.order_number, 'preferred', ep.preferred, 'required', ep.required, 'rule', ep.rule, 'created', ep.created) as p from terminology.entity_property ep where ep.code_system = cs.id and ep.sys_status = 'A' order by ep.order_number) ep) as properties ";

  public void save(CodeSystem codeSystem) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", codeSystem.getId());
    ssb.property("uri", codeSystem.getUri());
    ssb.property("publisher", codeSystem.getPublisher());
    ssb.property("name", codeSystem.getName());
    ssb.jsonProperty("title", codeSystem.getTitle());
    ssb.jsonProperty("description", codeSystem.getDescription());
    ssb.jsonProperty("purpose", codeSystem.getPurpose());
    ssb.property("hierarchy_meaning", codeSystem.getHierarchyMeaning());
    ssb.property("narrative", codeSystem.getNarrative());
    ssb.property("experimental", codeSystem.getExperimental());
    ssb.jsonProperty("identifiers", codeSystem.getIdentifiers());
    ssb.jsonProperty("contacts", codeSystem.getContacts());
    ssb.property("content", codeSystem.getContent());
    ssb.property("case_sensitive", codeSystem.getCaseSensitive() == null ? CaseSignificance.entire_term_case_insensitive : codeSystem.getCaseSensitive());
    ssb.property("sequence", codeSystem.getSequence());
    ssb.jsonProperty("copyright", codeSystem.getCopyright());
    ssb.property("base_code_system", codeSystem.getBaseCodeSystem());
    ssb.jsonProperty("settings", codeSystem.getSettings());
    ssb.property("sys_status", "A");

    SqlBuilder sb = ssb.buildUpsert("terminology.code_system", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public CodeSystem load(String codeSystem) {
    String sql = select + "from terminology.code_system cs where cs.sys_status = 'A' and cs.id = ?";
    return getBean(sql, bp, codeSystem);
  }

  public List<String> closure(String codeSystem) {
    String sql = "select * from unnest(terminology.code_system_closure(?))";
    return jdbcTemplate.queryForList(sql, String.class, codeSystem);
  }

  public QueryResult<CodeSystem> query(CodeSystemQueryParams params) {
    String join = getJoin(params);
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(distinct(cs.id)) from terminology.code_system cs " + join);
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from terminology.code_system cs " + join);
      sb.append(filter(params));
      sb.append(order(params, sortMap(params.getLang())));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(CodeSystemQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("where cs.sys_status = 'A'");
    if (CollectionUtils.isNotEmpty(params.getPermittedIds())) {
      sb.and().in("cs.id", params.getPermittedIds());
    }
    sb.appendIfNotNull("and cs.id = ?", params.getId());
    sb.appendIfNotNull(params.getIds(), (s, p) -> s.and().in("cs.id", p));
    sb.appendIfNotNull("and cs.id ~* ?", params.getIdContains());
    if (StringUtils.isNotEmpty(params.getUri())) {
      sb.and().in("cs.uri", params.getUri());
    }
    sb.appendIfNotNull("and cs.uri ~* ?", params.getUriContains());
    sb.appendIfNotNull("and cs.content = ?", params.getContent());
    sb.appendIfNotNull("and cs.description = ?", params.getDescription());
    sb.appendIfNotNull("and cs.description ~* ?", params.getDescriptionContains());
    sb.appendIfNotNull("and cs.base_code_system = ?", params.getBaseCodeSystem());
    sb.appendIfNotNull("and cs.publisher = ?", params.getPublisher());
    sb.appendIfNotNull("and terminology.jsonb_search(cs.title) like '%`' || terminology.search_translate(?) || '`%'", params.getName());
    sb.appendIfNotNull("and terminology.jsonb_search(cs.title) like '%' || terminology.search_translate(?) || '%'", params.getNameContains());
    if (StringUtils.isNotEmpty(params.getText())) {
      sb.append("and ( terminology.text_search(cs.id, cs.uri) like '%`' || terminology.search_translate(?) || '`%'" +
              "     or terminology.jsonb_search(cs.title) like '%`' || terminology.search_translate(?) || '`%' )",
          params.getText(), params.getText());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and ( terminology.text_search(cs.id, cs.uri) like '%' || terminology.search_translate(?) || '%'" +
              "     or terminology.jsonb_search(cs.title) like '%' || terminology.search_translate(?) || '%' )",
          params.getTextContains(), params.getTextContains());
    }
    sb.appendIfNotNull("and csv.version = ?", params.getVersionVersion());
    sb.appendIfNotNull("and csv.id = ?", params.getVersionId());
    sb.appendIfNotNull("and csv.status = ?", params.getVersionStatus());
    sb.appendIfNotNull("and csv.release_date >= ?", params.getVersionReleaseDateGe());
    sb.appendIfNotNull("and (csv.expiration_date <= ? or csv.expiration_date is null)", params.getVersionExpirationDateLe());
    sb.appendIfNotNull("and exists (select 1 from terminology.code_system_entity_version csev" +
        " where csev.code_system_entity_id = cse.id and csev.sys_status = 'A' and csev.id = ?)", params.getCodeSystemEntityVersionId());
    sb.appendIfNotNull("and c.code = ?", params.getConceptCode());
    sb.appendIfNotNull("and pv.id = ?", params.getPackageVersionId());
    sb.appendIfNotNull("and p.id = ?", params.getPackageId());
    sb.appendIfNotNull("and s.id = ?", params.getSpaceId());
    return sb;
  }

  private Map<String, String> sortMap(String lang) {
    Map<String, String> sortMap = new HashMap<>(Map.of(
        Ordering.id, "cs.id",
        Ordering.uri, "cs.uri"
    ));
    if (StringUtils.isNotEmpty(lang)) {
      sortMap.put(Ordering.description, "cs.description ->> '" + lang + "'");
    }
    if (StringUtils.isNotEmpty(lang)) {
      sortMap.put(Ordering.name, "cs.name ->> '" + lang + "'");
    }
    return sortMap;
  }

  public void cancel(String codeSystem) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.cancel_code_system(?)", codeSystem);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }

  private String getJoin(CodeSystemQueryParams params) {
    String join = "";
    if (CollectionUtils.isNotEmpty(Stream.of(
            params.getVersionVersion(), params.getVersionId(), params.getVersionStatus(),
            params.getVersionReleaseDateGe(), params.getVersionExpirationDateLe())
        .filter(Objects::nonNull).toList())) {
      join += "left join terminology.code_system_version csv on csv.code_system = cs.id and csv.sys_status = 'A' ";
    }
    if (CollectionUtils.isNotEmpty(Stream.of(
            params.getCodeSystemEntityVersionId(), params.getConceptCode())
        .filter(Objects::nonNull).toList())) {
      join += "left join terminology.code_system_entity cse on cse.code_system = cs.id and cse.sys_status = 'A' ";
    }
    if (CollectionUtils.isNotEmpty(Stream.of(params.getConceptCode()).filter(Objects::nonNull).toList())) {
      join += "left join terminology.concept c on c.id = cse.id and c.sys_status = 'A' ";
    }
    if (CollectionUtils.isNotEmpty(Stream.of(params.getPackageVersionId(), params.getPackageId(), params.getSpaceId()).filter(Objects::nonNull).toList())) {
      join += "left join sys.package_version_resource pvr on pvr.resource_type = 'code-system' and pvr.resource_id = cs.id and pvr.sys_status = 'A' " +
          "left join sys.package_version pv on pv.id = pvr.version_id and pv.sys_status = 'A' ";
    }
    if (CollectionUtils.isNotEmpty(Stream.of(params.getPackageId(), params.getSpaceId()).filter(Objects::nonNull).toList())) {
      join += "left join sys.package p on p.id = pv.package_id and p.sys_status = 'A' ";
    }
    if (CollectionUtils.isNotEmpty(Stream.of(params.getSpaceId()).filter(Objects::nonNull).toList())) {
      join += "left join sys.space s on s.id = p.space_id and s.sys_status = 'A' ";
    }
    return join;
  }

  public void changeId(String currentId, String newId) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.change_code_system_id(?,?)", currentId, newId);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }
}
