package com.kodality.termserver.observationdefinition;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.observationdefintion.ObservationDefinition;
import com.kodality.termserver.observationdefintion.ObservationDefinitionSearchParams;
import io.micronaut.core.util.StringUtils;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class ObservationDefinitionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ObservationDefinition.class, p -> {
    p.addColumnProcessor("names", PgBeanProcessor.fromJson());
    p.addColumnProcessor("alias", PgBeanProcessor.fromJson());
    p.addColumnProcessor("definition", PgBeanProcessor.fromJson());
    p.addColumnProcessor("keywords", PgBeanProcessor.fromJson());
    p.addColumnProcessor("structure", PgBeanProcessor.fromJson());
  });

  private final Map<String, String> orderMapping = Map.of("code", "od.code");

  public void save(ObservationDefinition def) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", def.getId());
    ssb.property("code", def.getCode());
    ssb.property("version", def.getVersion());
    ssb.property("publisher", def.getPublisher());
    ssb.property("url", def.getUrl());
    ssb.property("status", def.getStatus());
    ssb.jsonProperty("names", def.getNames());
    ssb.jsonProperty("alias", def.getAlias());
    ssb.jsonProperty("definition", def.getDefinition());
    ssb.jsonProperty("keywords", def.getKeywords());
    ssb.property("category", def.getCategory());
    ssb.property("time_precision", def.getTimePrecision());
    ssb.jsonProperty("structure", def.getStructure());
    SqlBuilder sb = ssb.buildSave("def.observation_definition", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    def.setId(id);
  }

  public ObservationDefinition load(Long id) {
    SqlBuilder sb = new SqlBuilder("select * from def.observation_definition where id = ? and sys_status = 'A'", id);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public ObservationDefinition load(String code) {
    SqlBuilder sb = new SqlBuilder("select * from def.observation_definition where code = ? and sys_status = 'A'", code);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public QueryResult<ObservationDefinition> search(ObservationDefinitionSearchParams params) {
    String join = "left join def.observation_definition_value odv on odv.observation_definition_id = od.id and odv.sys_status = 'A'";
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from def.observation_definition od " + join);
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select od.* from def.observation_definition od " +  join);
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ObservationDefinitionSearchParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("where od.sys_status = 'A'");
    if (StringUtils.isNotEmpty(params.getCodes())) {
      sb.and().in("od.code", params.getCodes());
    }
    if (StringUtils.isNotEmpty(params.getIdsNe())) {
      sb.and().notIn("od.id", params.getIdsNe(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getTypes())) {
      sb.and().in("odv.type", params.getTypes());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (od.code ~* ?" +
              "or exists (select 1 from jsonb_each_text(od.names) where value ~* ?)" +
              "or exists (select 1 from jsonb_each_text(od.alias) where value ~* ?)" +
              "or exists (select 1 from jsonb_each_text(od.definition) where value ~* ?)" +
              "or exists (select 1 from jsonb_each_text(od.keywords) where value ~* ?)" +
              ")", params.getTextContains(), params.getTextContains(), params.getTextContains(), params.getTextContains(), params.getTextContains());
    }
    return sb;
  }

}
