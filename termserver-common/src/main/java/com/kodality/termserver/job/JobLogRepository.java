package com.kodality.termserver.job;


import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.job.JobLog.JobDefinition;
import java.util.Map;
import javax.inject.Singleton;

@Singleton
public class JobLogRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(JobLog.class, p -> {
    p.addColumnProcessor("definition", PgBeanProcessor.fromJson());
    p.addColumnProcessor("execution", PgBeanProcessor.fromJson());
    p.addColumnProcessor("successes", PgBeanProcessor.fromJson());
    p.addColumnProcessor("warnings", PgBeanProcessor.fromJson());
    p.addColumnProcessor("errors", PgBeanProcessor.fromJson());
  });

  private final Map<String, String> orderMapping = Map.of("started", "started", "finished", "finished");
  private static final String SELECT = "select j.*, " +
      "jsonb_build_object('type', j.type, 'source', j.source) definition, " +
      "jsonb_build_object('started', j.started, 'finished', j.finished, 'status', j.status) execution" ;

  public JobLog load(Long id) {
    SqlBuilder sb = new SqlBuilder(SELECT + " from job.job_log j where j.sys_status = 'A' and j.id = ?", id);
    return getBean(sb.getSql(), bp, sb.getParams());
  }

  public Long create(JobDefinition definition) {
    SqlBuilder sb = new SqlBuilder("insert into job.job_log (type, source, status, started) " +
        "select ?,?,?, current_timestamp returning id",
        definition.getType(),
        definition.getSource(),
        JobExecutionStatus.RUNNING
    );
    return queryForObject(sb.getSql(), Long.class, sb.getParams());
  }

  public void finish(Long id) {
    SqlBuilder sb = new SqlBuilder("update job.job_log set status = ?, finished = current_timestamp ", JobExecutionStatus.COMPLETED);
    sb.append("where sys_status = 'A' and id = ?", id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void finish(JobLog jobLog, String status) {
    SqlBuilder sb = new SqlBuilder("update job.job_log set warnings = ?::jsonb, successes = ?::jsonb, errors = ?::jsonb, status = ?, finished = current_timestamp ",
        jobLog.getWarnings() == null ? null : JsonUtil.toJson(jobLog.getWarnings()),
        jobLog.getSuccesses() == null ? null : JsonUtil.toJson(jobLog.getSuccesses()),
        jobLog.getErrors() == null ? null : JsonUtil.toJson(jobLog.getErrors()),
        status
    );
    sb.append("where sys_status = 'A' and id = ?", jobLog.getId());
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public QueryResult<JobLog> query(JobLogQueryParams params) {
    return query(params, p -> {
      SqlBuilder sb = new SqlBuilder("select count(1) from job.job_log j where j.sys_status = 'A'");
      sb.append(filter(params));
      return queryForObject(sb.getSql(), Integer.class, sb.getParams());

    }, p -> {
      SqlBuilder sb = new SqlBuilder(SELECT + " from job.job_log j where j.sys_status = 'A'");

      sb.append(filter(params));
      sb.append(order(params, orderMapping));
      sb.append(limit(params));
      return getBeans(sb.getSql(), bp, sb.getParams());
    });
  }

  private SqlBuilder filter(JobLogQueryParams params) {
    SqlBuilder sb = new SqlBuilder();
    sb.appendIfNotNull(" and j.id = ?", params.getId());
    sb.appendIfNotNull(" and j.type = ?", params.getType());
    sb.appendIfNotNull(" and j.status = ?", params.getStatus());
    return sb;
  }
}
