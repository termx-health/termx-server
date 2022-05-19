package com.kodality.termserver.ts.mapset;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.mapset.MapSetQueryParams;
import com.kodality.termserver.mapset.MapSetQueryParams.Ordering;
import io.micronaut.core.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class MapSetRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(MapSet.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
  });

  public void create(MapSet mapSet) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", mapSet.getId());
    ssb.property("uri", mapSet.getUri());
    ssb.jsonProperty("names", mapSet.getNames());
    ssb.property("description", mapSet.getDescription());

    SqlBuilder sb = ssb.buildUpsert("map_set", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public MapSet load(String id) {
    String sql = "select * from map_set where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<MapSet> query(MapSetQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from map_set ms where ms.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select ms.* from map_set ms where ms.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(order(params, sortMap(params.getLang())));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(MapSetQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and ms.id = ?", params.getId());
    sb.appendIfNotNull("and ms.id ~* ?", params.getIdContains());
    sb.appendIfNotNull("and ms.uri = ?", params.getUri());
    sb.appendIfNotNull("and ms.uri ~* ?", params.getUriContains());
    sb.appendIfNotNull("and ms.description = ?", params.getDescription());
    sb.appendIfNotNull("and ms.description ~* ?", params.getDescriptionContains());
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(ms.names) where value = ?)", params.getName());
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(ms.names) where value ~* ?)", params.getNameContains());

    if (StringUtils.isNotEmpty(params.getText())) {
      sb.append("and (ms.id = ? or ms.uri = ? or ms.description = ? or exists (select 1 from jsonb_each_text(ms.names) where value = ?))", params.getText(), params.getText(), params.getText(), params.getText());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (ms.id ~* ? or ms.uri ~* ? or ms.description ~* ? or exists (select 1 from jsonb_each_text(ms.names) where value ~* ?))", params.getTextContains(), params.getTextContains(), params.getTextContains(), params.getTextContains());
    }

    sb.appendIfNotNull("and exists (select 1 from map_set_version msv where msv.map_set = ms.id and msv.sys_status = 'A' and msv.version = ?)", params.getVersionVersion());

    sb.appendIfNotNull("and exists (select 1 from code_system_entity_version csev " +
        "inner join map_set_association msa on msa.source_code_system_entity_version_id = csev.id and msa.sys_status = 'A' " +
        "where msa.map_set = ms.id and csev.sys_status = 'A' and csev.code = ?)", params.getAssociationSourceCode());
    sb.appendIfNotNull("and exists (select 1 from code_system cs " +
        "inner join code_system_entity cse on cse.code_system = cs.id and cse.sys_status = 'A' " +
        "inner join code_system_entity_version csev on csev.code_system_entity_id = cse.id and csev.sys_status = 'A' " +
        "inner join map_set_association msa on msa.source_code_system_entity_version_id = csev.id and msa.sys_status = 'A' " +
        "where msa.map_set = ms.id and cs.sys_status = 'A' and cs.id = ?)", params.getAssociationSourceSystem());
    sb.appendIfNotNull("and exists (select 1 from code_system cs " +
        "inner join code_system_entity cse on cse.code_system = cs.id and cse.sys_status = 'A' " +
        "inner join code_system_entity_version csev on csev.code_system_entity_id = cse.id and csev.sys_status = 'A' " +
        "inner join map_set_association msa on msa.source_code_system_entity_version_id = csev.id and msa.sys_status = 'A' " +
        "where msa.map_set = ms.id and cs.sys_status = 'A' and cs.uri = ?)", params.getAssociationSourceSystemUri());
    sb.appendIfNotNull("and exists (select 1 from code_system_version csv " +
        "inner join code_system_entity cse on cse.code_system = csv.code_system and cse.sys_status = 'A' " +
        "inner join code_system_entity_version csev on csev.code_system_entity_id = cse.id and csev.sys_status = 'A' " +
        "inner join map_set_association msa on msa.source_code_system_entity_version_id = csev.id and msa.sys_status = 'A' " +
        "where msa.map_set = ms.id and csv.sys_status = 'A' and csv.version = ?)", params.getAssociationSourceSystemVersion());

    sb.appendIfNotNull("and exists (select 1 from code_system cs " +
        "inner join code_system_entity cse on cse.code_system = cs.id and cse.sys_status = 'A' " +
        "inner join code_system_entity_version csev on csev.code_system_entity_id = cse.id and csev.sys_status = 'A' " +
        "inner join map_set_association msa on msa.target_code_system_entity_version_id = csev.id and msa.sys_status = 'A' " +
        "where msa.map_set = ms.id and cs.sys_status = 'A' and cs.uri = ?)", params.getAssociationTargetSystem());
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

}
