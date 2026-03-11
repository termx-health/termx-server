package com.kodality.commons.db.audit;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AditLog {
  private String schemaName;
  private String tableName;
  private Long transactionId;
  private String sessionUserName;
  private OffsetDateTime transactionDateTime;
  private Map<String, Object> changedFields;
}
