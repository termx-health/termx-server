package com.kodality.termserver.sys.server;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import io.micronaut.core.util.StringUtils;
import javax.inject.Singleton;

@Singleton
public class TerminologyServerRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(TerminologyServer.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
  });

  public void save(TerminologyServer server) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", server.getId());
    ssb.property("code", server.getCode());
    ssb.jsonProperty("names", server.getNames());
    ssb.property("root_url", server.getRootUrl());
    ssb.property("active", server.isActive());
    ssb.property("current_installation", server.isCurrentInstallation());

    SqlBuilder sb = ssb.buildSave("sys.terminology_server", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    server.setId(id);
  }

  public TerminologyServer load(Long id) {
    String sql = "select * from sys.terminology_server where id = ? and sys_status = 'A'";
    return getBean(sql, bp, id);
  }

  public TerminologyServer load(String code) {
    String sql = "select * from sys.terminology_server where code = ? and sys_status = 'A'";
    return getBean(sql, bp, code);
  }

  public TerminologyServer loadCurrentInstallation() {
    String sql = "select * from sys.terminology_server where current_installation is true and sys_status = 'A'";
    return getBean(sql, bp);
  }

  public QueryResult<TerminologyServer> query(TerminologyServerQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from sys.terminology_server ts where ts.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from sys.terminology_server ts where ts.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(TerminologyServerQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getCodes())) {
      sb.and().in("ts.code", params.getCodes());
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (ts.code ~* ? or cs.root_url ~* ? or exists (select 1 from jsonb_each_text(ts.names) where value ~* ?))",
          params.getTextContains(), params.getTextContains(), params.getTextContains());
    }
    sb.appendIfTrue(params.isCurrentInstallation(),"and ts.current_installation is true");
    sb.appendIfNotNull("and exists(select 1 from sys.space s where s.id = ? and ts.code in (select jsonb_array_elements_text(s.terminology_servers)) and s.sys_status = 'A')", params.getSpaceId());
    return sb;
  }

}
