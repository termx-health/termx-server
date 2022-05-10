package com.kodality.termserver.mapset;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetQueryParams extends QueryParams {
  private String name;
  private String uri;

  private String sourceEntityCode;
  private String sourceEntitySystem;
  private String sourceEntitySystemVersion;
  private String targetEntitySystem;

  private String versionVersion;
  private boolean decorated;
}
