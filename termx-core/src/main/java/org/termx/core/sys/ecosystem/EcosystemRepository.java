package org.termx.core.sys.ecosystem;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import jakarta.inject.Singleton;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.termx.sys.ecosystem.Ecosystem;
import org.termx.sys.ecosystem.EcosystemQueryParams;

@Singleton
public class EcosystemRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Ecosystem.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
  });

  public void save(Ecosystem ecosystem) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", ecosystem.getId());
    ssb.property("code", ecosystem.getCode());
    ssb.jsonProperty("names", ecosystem.getNames());
    ssb.property("format_version", ecosystem.getFormatVersion());
    ssb.property("description", ecosystem.getDescription());
    ssb.property("active", ecosystem.isActive());

    SqlBuilder sb = ssb.buildSave("sys.ecosystem", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    ecosystem.setId(id);
  }

  public void saveServers(Long ecosystemId, List<Long> serverIds) {
    SqlBuilder sb = new SqlBuilder("update sys.ecosystem_server set sys_status = 'C' where ecosystem_id = ? and sys_status = 'A'", ecosystemId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());

    if (serverIds != null) {
      for (Long serverId : serverIds) {
        SaveSqlBuilder ssb = new SaveSqlBuilder();
        ssb.property("ecosystem_id", ecosystemId);
        ssb.property("server_id", serverId);
        SqlBuilder insert = new SqlBuilder("insert into sys.ecosystem_server").append(ssb.buildInsertParams());
        jdbcTemplate.update(insert.getSql(), insert.getParams());
      }
    }
  }

  public List<Long> loadServerIds(Long ecosystemId) {
    String sql = "select server_id from sys.ecosystem_server where ecosystem_id = ? and sys_status = 'A'";
    return jdbcTemplate.queryForList(sql, Long.class, ecosystemId);
  }

  public Ecosystem load(Long id) {
    String sql = "select * from sys.ecosystem where id = ? and sys_status = 'A'";
    Ecosystem ecosystem = getBean(sql, bp, id);
    if (ecosystem != null) {
      ecosystem.setServerIds(loadServerIds(ecosystem.getId()));
    }
    return ecosystem;
  }

  public Ecosystem load(String code) {
    String sql = "select * from sys.ecosystem where code = ? and sys_status = 'A'";
    Ecosystem ecosystem = getBean(sql, bp, code);
    if (ecosystem != null) {
      ecosystem.setServerIds(loadServerIds(ecosystem.getId()));
    }
    return ecosystem;
  }

  public QueryResult<Ecosystem> query(EcosystemQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from sys.ecosystem e");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from sys.ecosystem e");
      sb.append(filter(params));
      sb.append(limit(params));
      List<Ecosystem> ecosystems = getBeans(sb.getSql(), bp, sb.getParams());
      ecosystems.forEach(e -> e.setServerIds(loadServerIds(e.getId())));
      return ecosystems;
    });
  }

  private SqlBuilder filter(EcosystemQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where e.sys_status = 'A'");
    sb.and().in("e.id", params.getPermittedIds());
    if (StringUtils.isNotEmpty(params.getIds())) {
      sb.and().in("e.id", params.getIds(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getCodes())) {
      sb.and().in("e.code", params.getCodes());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (e.code ~* ? or e.names::text ~* ?)", params.getTextContains(), params.getTextContains());
    }
    return sb;
  }
}
