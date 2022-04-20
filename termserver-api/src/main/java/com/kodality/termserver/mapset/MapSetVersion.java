package com.kodality.termserver.mapset;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MapSetVersion {
  private Long id;
  private String version;
  private String source;
  private List<String> supportedLanguages;
  private String description;
  private String status;
  private Date releaseDate;
  private OffsetDateTime created;

  private List<MapSetEntityVersion> entities;
}
