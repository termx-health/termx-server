package com.kodality.commons.db.audit;

import com.kodality.commons.model.QueryResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AuditLogService {
  @Inject
  private AuditLogRepository auditLogRepository;

  public QueryResult<AditLog> query(AuditLogQueryParams params) {
    return auditLogRepository.query(params);
  }
}
