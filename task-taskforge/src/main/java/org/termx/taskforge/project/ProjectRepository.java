package org.termx.taskforge.project;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import org.termx.taskforge.workflow.Workflow;
import org.termx.taskforge.workflow.WorkflowRepository;
import java.util.List;
import jakarta.inject.Singleton;

@Singleton
public class ProjectRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Project.class, p -> {
    p.addNamesColumnProcessor();
    p.addColumnProcessor("workflows", PgBeanProcessor.fromJson(JsonUtil.getListType(Workflow.class)));
  });

  public Long save(Project p) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", p.getId());
    ssb.property("institution", p.getInstitution());
    ssb.property("code", p.getCode());
    ssb.jsonProperty("names", p.getNames());
    SqlBuilder sb = ssb.buildSave("taskforge.project", "id");
    return jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }

  private String select() {
    return "select p.*" +
           ", (select jsonb_agg(" + WorkflowRepository.jsonb("w") + ")" +
           " from taskforge.workflow w where w.project_id = p.id and w.sys_status = 'A'" +
           ") as worfflows";
  }

  public Project load(Long id, String tenant) {
    SqlBuilder sb = new SqlBuilder(select() + " FROM taskforge.project p where p.id = ? and core.aclchk(p.id, ?) and p.sys_status = 'A'", id, tenant);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public Project load(String code, String institution, String tenant) {
    SqlBuilder sb = new SqlBuilder(select() + " FROM taskforge.project p where p.code = ? and p.institution = ? and core.aclchk(p.id, ?) and p.sys_status = 'A'",
        code, institution, tenant);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public List<Project> loadAll(String tenant) {
    SqlBuilder sb = new SqlBuilder(select() + " FROM taskforge.project p where core.aclchk(p.id, ?) and p.sys_status = 'A'", tenant);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

}
