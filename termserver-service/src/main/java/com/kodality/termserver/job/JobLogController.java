package com.kodality.termserver.job;

import com.kodality.commons.model.QueryResult;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/job-logs")
@RequiredArgsConstructor
public class JobLogController {
  protected final JobLogService jobLogService;

  @Get("/{id}")
  public JobLog get(@PathVariable Long id) {
    return jobLogService.get(id);
  }

  @Get("{?params*}")
  public QueryResult<JobLog> search(JobLogQueryParams params) {
    return jobLogService.search(params);
  }
}
