package com.kodality.termserver.mapset;

import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class MapSetEntityVersion {
  private Long id;
  private String mapSet;
  private String description;
  private String status;
  private OffsetDateTime created;
}
