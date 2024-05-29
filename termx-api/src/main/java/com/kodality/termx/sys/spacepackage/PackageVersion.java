package com.kodality.termx.sys.spacepackage;

import com.kodality.termx.sys.ResourceReference;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PackageVersion {
  private Long id;
  private String version;
  private String description;
  private List<PackageResource> resources;

  private Long packageId;

  @Getter
  @Setter
  @Accessors(chain = true)
  @Introspected
  public static class PackageResource extends ResourceReference {
    private Long id;
    private String terminologyServer;

    private Long versionId;
  }
}
