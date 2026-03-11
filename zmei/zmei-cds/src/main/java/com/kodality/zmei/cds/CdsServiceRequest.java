package com.kodality.zmei.cds;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CdsServiceRequest {
  private String hook;
  private String hookInstance;
  private String fhirServer;
  private Map<String, Object> context;
  private Map<String, Object> prefetch;
}
