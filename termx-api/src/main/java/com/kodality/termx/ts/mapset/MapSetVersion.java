package com.kodality.termx.ts.mapset;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class MapSetVersion extends MapSetVersionReference {
  private String mapSet;
  private String preferredLanguage;
  private LocalizedName description;
  private String algorithm;
  private LocalDate releaseDate;
  private LocalDate expirationDate;
  private OffsetDateTime created;

  private MapSetVersionScope scope;
  private MapSetVersionStatistics statistics;

  private List<MapSetAssociation> associations;

  @Getter
  @Setter
  public static class MapSetVersionScope {
    private String sourceType;
    private MapSetResourceReference sourceValueSet;
    private List<MapSetResourceReference> sourceCodeSystems;

    private String targetType;
    private MapSetResourceReference targetValueSet;
    private List<MapSetResourceReference> targetCodeSystems;
  }

  @Getter
  @Setter
  public static class MapSetVersionStatistics {
    private Long id;

    private OffsetDateTime createdAt;
    private String createdBy;

    private Integer sourceConcepts;
    private Integer equivalent;
    private Integer noMap;
    private Integer narrower;
    private Integer broader;
    private Integer unmapped;
    private Integer inactiveSources;
    private Integer inactiveTargets;

    private String mapSet;
    private MapSetVersionReference mapSetVersion;

    public MapSetVersionStatistics() {
      this.setSourceConcepts(0);
      this.setEquivalent(0);
      this.setNoMap(0);
      this.setNarrower(0);
      this.setBroader(0);
      this.setUnmapped(0);
      this.setInactiveSources(0);
      this.setInactiveTargets(0);
    }
  }

  @Getter
  @Setter
  public static class MapSetResourceReference {
    private String id;
    private String version;
    private String uri;
  }
}
