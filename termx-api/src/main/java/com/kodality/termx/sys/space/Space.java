package com.kodality.termx.sys.space;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.sys.spacepackage.Package;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class Space {
  private Long id;
  private String code;
  private LocalizedName names;
  private boolean active;
  private boolean shared;
  private Object acl;
  private List<String> terminologyServers;
  private SpaceIntegration integration;

  private List<Package> packages;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SpaceIntegration {
    private SpaceIntegrationGithub github;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SpaceIntegrationGithub {
    private String repo;
    private Map<String, String> dirs;
    private SpaceIntegrationImplementationGuide ig;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SpaceIntegrationImplementationGuide {
    private String header;
    private List<Long> pageContents;
    private List<SpaceIntegrationImplementationGuideMenuItem> menu;
  }

  @Getter
  @Setter
  public static class SpaceIntegrationImplementationGuideMenuItem {
    private String name;
    private Long page;
    private List<SpaceIntegrationImplementationGuideMenuItem> children; //actually only one level
  }
}
