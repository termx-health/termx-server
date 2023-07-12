package com.kodality.termx.fileimporter.mapset.utils;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class MapSetFileImportRequest {
  private String sourceValueSet;
  private String targetValueSet;
  private MapSetFileImportRequestMap map;
  private MapSetFileImportRequestVersion version;


  @Getter
  @Setter
  public static class MapSetFileImportRequestMap {
    private String id;
    private LocalizedName names;
    private String uri;
  }

  @Getter
  @Setter
  public static class MapSetFileImportRequestVersion {
    private String version;
    private LocalDate releaseDate;
  }
}