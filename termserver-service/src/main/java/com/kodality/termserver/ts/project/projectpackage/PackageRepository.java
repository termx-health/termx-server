package com.kodality.termserver.ts.project.projectpackage;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PackageRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(Package.class);

  public void save(Package p, Long projectId) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", p.getId());
    ssb.property("project_id", projectId);
    ssb.property("code", p.getCode());
    ssb.property("status", p.getStatus());
    ssb.property("git", p.getGit());

    SqlBuilder sb = ssb.buildSave("terminology.package", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    p.setId(id);
  }

  public Package load(Long id) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.package where id = ? and sys_status = 'A'", id);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public Package load(Long projectId, String code) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.package where project_id = ? and code = ? and sys_status = 'A'", projectId, code);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public List<Package> loadAll(Long projectId) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.package where project_id = ? and sys_status = 'A'", projectId);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public void delete(Long id) {
    SqlBuilder sb = new SqlBuilder("update terminology.package set sys_status = 'C' where id = ? and sys_status = 'A'", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
