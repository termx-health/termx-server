package com.kodality.termx.terminology.fileimporter.mapset.utils;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetVersionScope;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class MapSetFileImportRequest {
  private String type; // csv; json; fsh;
  private String url;

  private MapSetFileImportRequestMap mapSet;
  private MapSetFileImportRequestVersion mapSetVersion;

  private boolean cleanRun;
  private boolean cleanAssociationRun;

  @Getter
  @Setter
  public static class MapSetFileImportRequestMap {
    private String id;
    private LocalizedName title;
    private String uri;
    private LocalizedName description;
  }

  @Getter
  @Setter
  public static class MapSetFileImportRequestVersion {
    private String status;
    private String version;
    private LocalDate releaseDate;
    private MapSetVersionScope scope;
  }
}
