package com.kodality.termx.core.sys.release;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.sys.release.Release;
import com.kodality.termx.sys.release.ReleaseQueryParams;
import io.micronaut.core.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class ReleaseRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(Release.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
  });

  public void save(Release release) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", release.getId());
    ssb.property("code", release.getCode());
    ssb.jsonProperty("names", release.getNames());
    ssb.property("planned", release.getPlanned());
    ssb.property("release_date", release.getReleaseDate());
    ssb.property("status", release.getStatus());

    SqlBuilder sb = ssb.buildSave("sys.release", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    release.setId(id);
  }

  public Release load(Long id) {
    String sql = "select * from sys.release where id = ? and sys_status = 'A'";
    return getBean(sql, bp, id);
  }

  public Release load(String code) {
    String sql = "select * from sys.release where code = ? and sys_status = 'A'";
    return getBean(sql, bp, code);
  }

  public QueryResult<Release> query(ReleaseQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from sys.release r");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from sys.release r");
      sb.append(filter(params));
      sb.append(order(params, sortMap()));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ReleaseQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where r.sys_status = 'A'");
    sb.and().in("r.id", params.getPermittedIds());
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (r.code ~* ? or exists (select 1 from jsonb_each_text(r.names) where value ~* ?))",
          params.getTextContains(), params.getTextContains());
    }
    return sb;
  }

  private Map<String, String> sortMap() {
    Map<String, String> sortMap = new HashMap<>(Map.of("code", "r.code"));
    return sortMap;
  }

  public void changeStatus(Long id, String status) {
    String sql = "update sys.release set status = ? where id = ? and sys_status = 'A' and status <> ?";
    jdbcTemplate.update(sql, status, id, status);
  }
}
