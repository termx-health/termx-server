package com.kodality.termserver.mapset;

import com.kodality.commons.model.LocalizedName;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSet {
  private String id;
  private LocalizedName names;
  private String description;

  private List<MapSetVersion> versions;
}
