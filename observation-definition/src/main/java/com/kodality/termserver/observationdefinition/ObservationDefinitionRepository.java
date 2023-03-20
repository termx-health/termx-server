package com.kodality.termserver.observationdefinition;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.observationdefintion.ObservationDefinition;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class ObservationDefinitionRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ObservationDefinition.class, p -> {
    p.addColumnProcessor("names", PgBeanProcessor.fromJson());
    p.addColumnProcessor("alias", PgBeanProcessor.fromJson());
    p.addColumnProcessor("definition", PgBeanProcessor.fromJson());
    p.addColumnProcessor("keywords", PgBeanProcessor.fromJson());
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
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from def.observation_definition od");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from def.observation_definition od");
      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ObservationDefinitionSearchParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.append("where od.sys_status = 'A'");
    return sb;
  }

}
