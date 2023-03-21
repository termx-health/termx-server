package com.kodality.termserver.terminology.codesystem;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.db.util.PgUtil;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ts.ContactDetail;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemQueryParams.Ordering;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class CodeSystemRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystem.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("contacts", PgBeanProcessor.fromJson(JsonUtil.getListType(ContactDetail.class)));
    bp.addColumnProcessor("supported_languages", PgBeanProcessor.fromArray());
  });

  public void save(CodeSystem codeSystem) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", codeSystem.getId());
    ssb.property("uri", codeSystem.getUri());
    ssb.jsonProperty("names", codeSystem.getNames());
    ssb.property("content", codeSystem.getContent());
    ssb.jsonProperty("contacts", codeSystem.getContacts());
    ssb.property("case_sensitive", codeSystem.getCaseSensitive());
    ssb.property("narrative", codeSystem.getNarrative());
    ssb.property("description", codeSystem.getDescription());
    ssb.property("supported_languages", "?::text[]", PgUtil.array(codeSystem.getSupportedLanguages()));
    ssb.property("base_code_system", codeSystem.getBaseCodeSystem());
    ssb.property("sys_status", "A");

    SqlBuilder sb = ssb.buildUpsert("terminology.code_system", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public CodeSystem load(String codeSystem) {
    String sql = "select * from terminology.code_system where sys_status = 'A' and id = ?";
    return getBean(sql, bp, codeSystem);
  }

  public List<String> closure(String codeSystem) {
    String sql = "select * from unnest(terminology.code_system_closure(?))";
    return jdbcTemplate.queryForList(sql, String.class, codeSystem);
  }

  public QueryResult<CodeSystem> query(CodeSystemQueryParams params) {
    String join =
        "left join terminology.code_system_version csv on csv.code_system = cs.id and csv.sys_status = 'A' " +
        "left join terminology.code_system_entity cse on cse.code_system = cs.id and cse.sys_status = 'A' " +
        "left join terminology.concept c on c.id = cse.id and c.sys_status = 'A' " +
        "left join terminology.code_system_entity_version csev on csev.code_system_entity_id = cse.id and csev.sys_status = 'A' " +
        "left join terminology.package_version_resource pvr on pvr.resource_type = 'code-system' and pvr.resource_id = cs.id and pvr.sys_status = 'A' " +
        "left join terminology.package_version pv on pv.id = pvr.version_id and pv.sys_status = 'A' " +
        "left join terminology.package p on p.id = pv.package_id and p.sys_status = 'A' " +
        "left join terminology.project pr on pr.id = p.project_id and pr.sys_status = 'A' ";
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(distinct(cs.id)) from terminology.code_system cs " + join);
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select distinct on (cs.id) cs.* from terminology.code_system cs " + join);
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
    sb.appendIfNotNull("and cs.uri = ?", params.getUri());
    sb.appendIfNotNull("and cs.uri ~* ?", params.getUriContains());
    sb.appendIfNotNull("and cs.content = ?", params.getContent());
    sb.appendIfNotNull("and cs.description = ?", params.getDescription());
    sb.appendIfNotNull("and cs.description ~* ?", params.getDescriptionContains());
    sb.appendIfNotNull("and cs.base_code_system = ?", params.getBaseCodeSystem());
    sb.appendIfNotNull("and cs.exists (select 1 from jsonb_each_text(cs.names) where value = ?)", params.getName());
    sb.appendIfNotNull("and cs.exists (select 1 from jsonb_each_text(cs.names) where value ~* ?)", params.getNameContains());
    if (StringUtils.isNotEmpty(params.getText())) {
      sb.append("and (cs.id = ? or cs.uri = ? or cs.description = ? or exists (select 1 from jsonb_each_text(cs.names) where value = ?))",
          params.getText(), params.getText(), params.getText(), params.getText());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (cs.id ~* ? or cs.uri ~* ? or cs.description ~* ? or exists (select 1 from jsonb_each_text(cs.names) where value ~* ?))",
          params.getTextContains(), params.getTextContains(), params.getTextContains(), params.getTextContains());
    }
    sb.appendIfNotNull("and c.code = ?", params.getConceptCode());
    sb.appendIfNotNull("and csv.id = ?", params.getVersionId());
    sb.appendIfNotNull("and csv.version = ?", params.getVersionVersion());
    sb.appendIfNotNull("and csv.status = ?", params.getVersionStatus());
    sb.appendIfNotNull("and csv.source = ?", params.getVersionSource());
    sb.appendIfNotNull("and csv.release_date >= ?", params.getVersionReleaseDateGe());
    sb.appendIfNotNull("and (csv.expiration_date <= ? or expiration_date is null)", params.getVersionExpirationDateLe());
    sb.appendIfNotNull("and csev.id = ?", params.getCodeSystemEntityVersionId());
    sb.appendIfNotNull("and pv.id = ?", params.getPackageVersionId());
    sb.appendIfNotNull("and p.id = ?", params.getPackageId());
    sb.appendIfNotNull("and pr.id = ?", params.getProjectId());
    return sb;
  }

  private Map<String, String> sortMap(String lang) {
    Map<String, String> sortMap = new HashMap<>(Map.of(
        Ordering.id, "cs.id",
        Ordering.uri, "cs.uri",
        Ordering.description, "cs.description"
    ));
    if (StringUtils.isNotEmpty(lang)) {
      sortMap.put(Ordering.name, "cs.names ->> '" + lang + "'");
    }
    return sortMap;
  }

  public void cancel(String codeSystem) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.cancel_code_system(?)", codeSystem);
    jdbcTemplate.queryForObject(sb.getSql(), sb.getParams(), Void.class);
  }
}
