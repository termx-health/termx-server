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
  private String nameContains;
  private String uri;
  private String uriContains;

  private String associationSourceCode;
  private String associationSourceSystem;
  private String associationSourceSystemUri;
  private String associationSourceSystemVersion;
  private String associationTargetSystem;
  private boolean associationsDecorated;

  private String versionVersion;
  private boolean versionsDecorated;
}
