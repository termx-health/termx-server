package com.kodality.termx.sys.server;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.db.util.PgUtil;
import com.kodality.commons.model.QueryResult;
import java.util.Arrays;
import java.util.List;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class TerminologyServerRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(TerminologyServer.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("kind", PgBeanProcessor.fromJson());
  });

  public void save(TerminologyServer server) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", server.getId());
    ssb.property("code", server.getCode());
    ssb.jsonProperty("names", server.getNames());
    ssb.jsonProperty("kind", server.getKind());
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
      SqlBuilder sb = new SqlBuilder("select count(1) from sys.terminology_server ts");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from sys.terminology_server ts");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(TerminologyServerQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where ts.sys_status = 'A'");
    if (params.getSpaceId() != null) {
      String sql = "and exists(select 1 from sys.space s where s.id = ? and ts.code in (select jsonb_array_elements_text(s.terminology_servers)) and s.sys_status = 'A')";
      sb.append(sql, params.getSpaceId());
    }
    if (StringUtils.isNotEmpty(params.getCodes())) {
      sb.and().in("ts.code", params.getCodes());
    }
    if (StringUtils.isNotEmpty(params.getKinds())) {
      List<String> kinds = Arrays.stream(StringUtils.split(params.getKinds(), ",")).toList();
      sb.and("ts.kind ??| ?::text[]", PgUtil.array(kinds));
    }
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      String sql = "and (ts.code ~* ? or cs.root_url ~* ? or exists (select 1 from jsonb_each_text(ts.names) where value ~* ?))";
      sb.append(sql, params.getTextContains(), params.getTextContains(), params.getTextContains());
    }
    sb.appendIfTrue(params.isCurrentInstallation(), "and ts.current_installation is true");
    return sb;
  }

}
