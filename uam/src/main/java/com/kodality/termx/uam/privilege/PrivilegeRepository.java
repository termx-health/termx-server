package com.kodality.termx.uam.privilege;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Privilege;
import com.kodality.termx.auth.PrivilegeQueryParams;
import com.kodality.termx.core.auth.SessionInfo;
import com.kodality.termx.core.auth.SessionStore;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Singleton
public class PrivilegeRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Privilege.class, PgBeanProcessor::addNamesColumnProcessor);

  public Privilege load(Long id) {
    String sql = "select * from uam.privilege where id = ? and sys_status = 'A'";
    return getBean(sql, bp, id);
  }

  public Privilege load(String code) {
    String sql = "select * from uam.privilege where code = ? and sys_status = 'A'";
    return getBean(sql, bp, code);
  }

  public QueryResult<Privilege> query(PrivilegeQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from uam.privilege where sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from uam.privilege where sys_status = 'A'");
      sb.append(filter(params));
      sb.append(order(params, Map.<String, Function<String, SqlBuilder>>of(
          "code", v -> new SqlBuilder("code"),
          "name", v -> new SqlBuilder("names ->> ?", SessionStore.get().map(SessionInfo::getLang).orElse("en"))
      )));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(PrivilegeQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    if (StringUtils.isNotEmpty(params.getCode())) {
      sb.and().in("code", params.getCode());
    }
    sb.appendIfNotNull("and code ~* ?", params.getCodeContains());
    return sb;
  }

  public void save(Privilege privilege) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", privilege.getId());
    ssb.property("code", privilege.getCode());
    ssb.jsonProperty("names", privilege.getNames());
    SqlBuilder sb = ssb.buildSave("uam.privilege", "id");

    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    privilege.setId(id);
  }

  public void delete(Long id) {
    SqlBuilder sb = new SqlBuilder("update uam.privilege set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
