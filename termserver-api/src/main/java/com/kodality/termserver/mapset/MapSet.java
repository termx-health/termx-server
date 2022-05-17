package com.kodality.termserver.mapset;

import com.kodality.commons.model.LocalizedName;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class MapSet {
  private String id;
  private String uri;
  private LocalizedName names;
  private String description;

  private List<MapSetVersion> versions;
  private List<MapSetAssociation> associations;
}
