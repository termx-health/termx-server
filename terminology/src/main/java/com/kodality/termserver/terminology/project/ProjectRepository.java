package com.kodality.termserver.terminology.project;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ts.project.Project;
import com.kodality.termserver.ts.project.ProjectQueryParams;
import javax.inject.Singleton;

@Singleton
public class ProjectRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Project.class, bp -> {
    bp.addColumnProcessor("names", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("acl", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("terminology_servers", PgBeanProcessor.fromJson(JsonUtil.getListType(String.class)));
  });

  public void save(Project project) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", project.getId());
    ssb.property("code", project.getCode());
    ssb.jsonProperty("names", project.getNames());
    ssb.property("active", project.isActive());
    ssb.property("shared", project.isShared());
    ssb.jsonProperty("acl", project.getAcl());
    ssb.jsonProperty("terminology_servers", project.getTerminologyServers());

    SqlBuilder sb = ssb.buildSave("terminology.project", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    project.setId(id);
  }

  public Project load(Long id) {
    String sql = "select * from terminology.project where id = ? and sys_status = 'A'";
    return getBean(sql, bp, id);
  }

  public Project load(String code) {
    String sql = "select * from terminology.project where code = ? and sys_status = 'A'";
    return getBean(sql, bp, code);
  }

  public QueryResult<Project> query(ProjectQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from terminology.project p where p.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }, p -> {
      SqlBuilder sb = new SqlBuilder("select * from terminology.project p where p.sys_status = 'A'");
      sb.append(filter(params));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(ProjectQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    return sb;
  }

}
