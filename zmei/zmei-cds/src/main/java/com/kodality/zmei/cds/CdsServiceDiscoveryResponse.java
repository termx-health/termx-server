package com.kodality.zmei.cds;

import com.kodality.zmei.fhir.resource.Resource;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class CdsServiceDiscoveryResponse {
  private List<CdsService> services;

  public CdsServiceDiscoveryResponse(List<CdsService> services) {
    this.services = services;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  @NoArgsConstructor
  public static class CdsService {
    private String hook;
    private String title;
    private String description;
    private String id;
    private Map<String, Resource> prefetch;

    public CdsService(String hook, String id, String description) {
      this.hook = hook;
      this.description = description;
      this.id = id;
    }
  }
}
