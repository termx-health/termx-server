package com.kodality.termx.sys.server;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TerminologyServerQueryParams extends QueryParams {
  private Long spaceId;
  private String codes;
  private String kinds;
  private String textContains;
  private boolean currentInstallation;
}
