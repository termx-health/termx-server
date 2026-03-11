package com.kodality.commons.db.audit;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditLogQueryParams extends QueryParams {
  private String keys;
}
