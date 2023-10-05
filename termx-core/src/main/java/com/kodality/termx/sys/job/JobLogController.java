package com.kodality.termx.sys.job;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Authorized;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/job-logs")
@RequiredArgsConstructor
public class JobLogController {
  protected final JobLogService jobLogService;

  //TODO: auth
  @Authorized
  @Get("{?params*}")
  public QueryResult<JobLog> queryJobLogs(JobLogQueryParams params) {
    return jobLogService.query(params);
  }

  @Authorized
  @Get("/{id}")
  public JobLog getJobLog(@PathVariable Long id) {
    return jobLogService.get(id);
  }
}
