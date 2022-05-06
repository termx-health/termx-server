package com.kodality.termserver.ts.namingsystem;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.namingsystem.NamingSystem;
import com.kodality.termserver.namingsystem.NamingSystemQueryParams;
import javax.inject.Singleton;

@Singleton
public class NamingSystemRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(NamingSystem.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("identifiers", PgBeanProcessor.fromJson());
  });

  public void create(NamingSystem namingSystem) {
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

    SqlBuilder sb = ssb.buildUpsert("naming_system", "id");
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public NamingSystem load(String id) {
    String sql = "select * from naming_system where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public QueryResult<NamingSystem> query(NamingSystemQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from naming_system ns where ns.sys_status = 'A' ");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select ns.* from naming_system ns where ns.sys_status = 'A' ");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(NamingSystemQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull("and exists (select 1 from jsonb_each_text(ns.names) where value ~* ?)", params.getName());
    sb.appendIfNotNull("and code_system = ? ", params.getCodeSystem());
    return sb;
  }
}
