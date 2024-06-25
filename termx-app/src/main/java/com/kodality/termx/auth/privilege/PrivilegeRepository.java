package com.kodality.termx.auth.privilege;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Privilege;
import com.kodality.termx.auth.PrivilegeQueryParams;
import io.micronaut.core.util.StringUtils;
import java.util.Map;
import jakarta.inject.Singleton;

@Singleton
public class PrivilegeRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Privilege.class, PgBeanProcessor::addNamesColumnProcessor);

  public void save(Privilege privilege) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", privilege.getId());
    ssb.property("code", privilege.getCode());
    ssb.jsonProperty("names", privilege.getNames());
    SqlBuilder sb = ssb.buildSave("auth.privilege", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    privilege.setId(id);
  }

  public Privilege load(Long id) {
    String sql = "select * from auth.privilege where id = ? and sys_status = 'A'";
    return getBean(sql, bp, id);
  }

  public Privilege load(String code) {
    String sql = "select * from auth.privilege where code = ? and sys_status = 'A'";
    return getBean(sql, bp, code);
  }

  public QueryResult<Privilege> query(PrivilegeQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from auth.privilege where sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from auth.privilege where sys_status = 'A'");
      sb.append(filter(params));
      sb.append(order(params, Map.of("code", "code")));
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

  public void delete(Long id) {
    SqlBuilder sb = new SqlBuilder("update auth.privilege set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
