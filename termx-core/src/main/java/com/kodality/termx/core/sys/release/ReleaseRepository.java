package com.kodality.termx.core.sys.release;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.sys.release.Release;
import com.kodality.termx.sys.release.ReleaseQueryParams;
import com.kodality.termx.sys.release.ReleaseResource;
import io.micronaut.core.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import jakarta.inject.Singleton;

@Singleton
public class ReleaseRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(Release.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("authors", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("resources", PgBeanProcessor.fromJson(JsonUtil.getListType(ReleaseResource.class)));
  });

  private static final String select = "select r.*, " +
      "(select jsonb_agg(rr.r) from (select json_build_object(" +
      "               'id', rr.id, " +
      "               'resourceType', rr.resource_type, " +
      "               'resourceId', rr.resource_id,"+
      "               'resourceVersion', rr.resource_version,"+
      "               'resourceNames', rr.resource_names"+
      ") as r " +
      "from sys.release_resource rr where rr.release_id = r.id and rr.sys_status = 'A') rr) as resources ";


  public void save(Release release) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", release.getId());
    ssb.property("code", release.getCode());
    ssb.jsonProperty("names", release.getNames());
    ssb.property("planned", release.getPlanned());
    ssb.property("release_date", release.getReleaseDate());
    ssb.property("status", release.getStatus());
    ssb.jsonProperty("authors", release.getAuthors());
    ssb.property("terminology_server", release.getTerminologyServer());

    SqlBuilder sb = ssb.buildSave("sys.release", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    release.setId(id);
  }

  public Release load(Long id) {
    String sql = select + "from sys.release r where r.id = ? and r.sys_status = 'A'";
    return getBean(sql, bp, id);
  }

  public Release load(String code) {
    String sql = select + "from sys.release r where r.code = ? and r.sys_status = 'A'";
    return getBean(sql, bp, code);
  }

  public QueryResult<Release> query(ReleaseQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from sys.release r");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder(select + "from sys.release r");
      sb.append(filter(params));
      sb.append(order(params, sortMap()));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ReleaseQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where r.sys_status = 'A'");
    sb.and().in("r.id", params.getPermittedIds());
    sb.and().in("r.status", params.getStatus());
    if (StringUtils.isNotEmpty(params.getTextContains())) {
      sb.append("and (r.code ~* ? or exists (select 1 from jsonb_each_text(r.names) where value ~* ?))",
          params.getTextContains(), params.getTextContains());
    }
    if (StringUtils.isNotEmpty(params.getResource())) {
      String[] resource = params.getResource().split("\\|");
      sb.append("and exists (select 1 from sys.release_resource rr where rr.release_id = r.id and rr.sys_status = 'A' ");
      if (resource.length > 0) {
        sb.append( "and rr.resource_type = ?", resource[0]);
      }
      if (resource.length > 1) {
        sb.append( "and rr.resource_id = ?", resource[1]);
      }
      if (resource.length > 2) {
        sb.append( "and rr.resource_version = ?", resource[2]);
      }
      sb.append(")");
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
