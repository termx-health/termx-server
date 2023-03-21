package com.kodality.termserver.terminology.namingsystem;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.namingsystem.NamingSystem;
import com.kodality.termserver.ts.namingsystem.NamingSystemQueryParams;
import com.kodality.termserver.ts.namingsystem.NamingSystemQueryParams.Ordering;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class NamingSystemRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(NamingSystem.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("identifiers", PgBeanProcessor.fromJson());
  });

  public void save(NamingSystem namingSystem) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", namingSystem.getId());
    ssb.jsonProperty("names", namingSystem.getNames());
    ssb.property("kind", namingSystem.getKind());
    ssb.property("code_system", namingSystem.getCodeSystem());
    ssb.property("source", namingSystem.getSource());
    ssb.property("description", namingSystem.getDescription());
    ssb.jsonProperty("identifiers", namingSystem.getIdentifiers());
    ssb.property("status", namingSystem.getStatus());
    ssb.property("created", namingSystem.getCreated());
    ssb.property("sys_status", "A");

    SqlBuilder sb = ssb.buildUpsert("terminology.naming_system", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public NamingSystem load(String id) {
    String sql = "select * from terminology.naming_system where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<NamingSystem> query(NamingSystemQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.naming_system ns where ns.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select ns.* from terminology.naming_system ns where ns.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(order(params, sortMap(params.getLang())));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(NamingSystemQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and id = ? ", params.getId());
    sb.appendIfNotNull("and id ~* ? ", params.getIdContains());
    if (CollectionUtils.isNotEmpty(params.getPermittedIds())) {
      sb.and().in("id", params.getPermittedIds());
    }
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(ns.names) where value = ?)", params.getName());
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(ns.names) where value ~* ?)", params.getNameContains());
    sb.appendIfNotNull("and source = ? ", params.getSource());
    sb.appendIfNotNull("and source ~* ? ", params.getSourceContains());
    sb.appendIfNotNull("and kind = ? ", params.getKind());
    sb.appendIfNotNull("and kind ~* ? ", params.getKindContains());
    sb.appendIfNotNull("and status = ? ", params.getStatus());
    sb.appendIfNotNull("and status ~* ? ", params.getStatusContains());
    sb.appendIfNotNull("and description = ? ", params.getDescription());
    sb.appendIfNotNull("and description ~* ? ", params.getDescriptionContains());
    sb.appendIfNotNull("and code_system = ? ", params.getCodeSystem());
    if (StringUtils.isNotEmpty(params.getText())) {
      sb.append("and (id = ? or description = ? or source = ? or kind = ? or status = ? " +
              "or exists (select 1 from jsonb_each_text(ns.names) where value = ?) or exists (select 1 from jsonb_array_elements(ns.identifiers) obj where exists (select 1 from jsonb_each_text(obj) where value = ?)))"
          , params.getText(), params.getText(), params.getText(), params.getText(), params.getText(), params.getText(), params.getText());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (id ~* ? or description ~* ? or source ~* ? or kind ~* ? or status ~* ? " +
              "or exists (select 1 from jsonb_each_text(ns.names) where value ~* ?) or exists (select 1 from jsonb_array_elements(ns.identifiers) obj where exists (select 1 from jsonb_each_text(obj) where value ~* ?)))"
          , params.getTextContains(), params.getTextContains(), params.getTextContains(), params.getTextContains(), params.getTextContains(),
          params.getTextContains(), params.getTextContains());
    }

    return sb;
  }

  public void activate(String id) {
    String sql = "update terminology.naming_system set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.active, id, PublicationStatus.active);
  }

  public void retire(String id) {
    String sql = "update terminology.naming_system set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, PublicationStatus.retired, id, PublicationStatus.retired);
  }

  private Map<String, String> sortMap(String lang) {
    Map<String, String> sortMap = new HashMap<>(Map.of(
        Ordering.id, "id",
        Ordering.source, "source",
        Ordering.kind, "kind",
        Ordering.status, "status"
    ));
    if (StringUtils.isNotEmpty(lang)) {
      sortMap.put(Ordering.name, "ns.names ->> '" + lang + "'");
    }
    return sortMap;
  }

  public void cancel(String id) {
    SqlBuilder sb = new SqlBuilder("update terminology.naming_system set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
