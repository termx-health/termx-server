package com.kodality.termx.sys.lorque;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import javax.inject.Singleton;

@Singleton
public class LorqueProcessRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(LorqueProcess.class);

  public LorqueProcess load(Long id) {
    return getBean("select * from sys.lorque_process where id = ? and sys_status = 'A'", bp, id);
  }

  public String getStatus(Long id) {
    return jdbcTemplate.queryForObject("select status from sys.lorque_process where id = ? and sys_status = 'A'", String.class, id);
  }

  public Long save(LorqueProcess process) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", process.getId());
    ssb.property("process_name", process.getProcessName());
    ssb.property("status", process.getStatus());
    ssb.property("started", process.getStarted());
    ssb.property("finished", process.getFinished());
    ssb.property("result", process.getResult());
    ssb.property("result_type", process.getResultType());
    SqlBuilder sb = ssb.buildSave("sys.lorque_process", "id");
    return jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }

  public void cleanup(int daysInterval) {
    SqlBuilder sb = new SqlBuilder("delete from sys.lorque_process where started < now() - (?||' days')::interval", daysInterval);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }
}
