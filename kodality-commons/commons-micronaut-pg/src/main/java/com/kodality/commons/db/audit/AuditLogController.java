package com.kodality.commons.db.audit;

import com.kodality.commons.model.QueryResult;
import io.micronaut.http.annotation.Get;
import jakarta.inject.Inject;

//TODO: auth?
//@Controller("/audit-log")
public class AuditLogController {
  @Inject
  private AuditLogService auditLogService;

  @Get("{?params*}")
  public QueryResult<AditLog> query(AuditLogQueryParams params) {
    return auditLogService.query(params);
  }
}
