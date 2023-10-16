package com.kodality.termx.core.sys.space;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.sys.space.Space;
import com.kodality.termx.sys.space.SpaceQueryParams;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class SpaceRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Space.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("acl", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("integration", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("terminology_servers", PgBeanProcessor.fromJson(JsonUtil.getListType(String.class)));
  });

  public void save(Space space) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", space.getId());
    ssb.property("code", space.getCode());
    ssb.jsonProperty("names", space.getNames());
    ssb.property("active", space.isActive());
    ssb.property("shared", space.isShared());
    ssb.jsonProperty("acl", space.getAcl());
    ssb.jsonProperty("integration", space.getIntegration());
    ssb.jsonProperty("terminology_servers", space.getTerminologyServers());

    SqlBuilder sb = ssb.buildSave("sys.space", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    space.setId(id);
  }

  public Space load(Long id) {
    String sql = "select * from sys.space where id = ? and sys_status = 'A'";
    return getBean(sql, bp, id);
  }

  public Space load(String code) {
    String sql = "select * from sys.space where code = ? and sys_status = 'A'";
    return getBean(sql, bp, code);
  }

  public QueryResult<Space> query(SpaceQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from sys.space s");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from sys.space s");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(SpaceQueryParams params) {
    SqlBuilder sb = new SqlBuilder("where s.sys_status = 'A'");
    sb.and().in("s.id", params.getPermittedIds());
    if (StringUtils.isNotEmpty(params.getIds())) {
      sb.and().in("id", params.getIds(), Long::valueOf);
    }
    if (StringUtils.isNotEmpty(params.getCodes())) {
      sb.and().in("code", params.getCodes());
    }
    if (StringUtils.isNotEmpty(params.getResource())) {
      sb.append("and exists (select 1 from sys.package_version_resource pvr where pvr.sys_status = 'A' and")
          .pipe("pvr.resource_type", "pvr.resource_id", params.getResource())
          .append(" and pvr.version_id in (select id from sys.package_version pv where pv.sys_status = 'A'" +
                  " and pv.package_id in (select id from sys.package p where p.space_id = s.id and p.sys_status = 'A'))")
          .append(")");
    }
    return sb;
  }

}
