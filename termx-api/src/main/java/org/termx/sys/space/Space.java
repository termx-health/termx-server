package org.termx.sys.space;

import com.kodality.commons.model.LocalizedName;
import org.termx.sys.spacepackage.Package;
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
  private boolean globalSearch;
  private Object acl;
  private List<String> terminologyServers;
  private SpaceIntegration integration;

  private LocalizedName description;
  private String defaultLanguage;
  private List<String> languages;
  private String siteUrl;

  // Static-site (mdbook) generator config, exported into space.json so the wiki is the single place
  // to configure the published site. A repo's .mdbook/config.yml, if present, still overrides these.
  private String ssgSkin;
  private String ssgThemeAccent;
  private Boolean ssgThemeSwitcher;
  private String ssgFooterMessage;
  private String ssgFooterCopyright;
  private String ssgTxServer;
  private Boolean ssgSearch;
  private String ssgLogo;

  private List<Package> packages;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SpaceIntegration {
    private SpaceIntegrationGithub github;
    private SpaceIntegrationMsDevops msDevops;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SpaceIntegrationGithub {
    private String repo;
    private Map<String, String> dirs;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SpaceIntegrationMsDevops {
    private String repo;
    private Map<String, String> dirs;
  }
}
