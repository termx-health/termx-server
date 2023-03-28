package com.kodality.termserver.ts.project.server;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TerminologyServerQueryParams extends QueryParams {
  private String codes;
  private Long projectId;
  private boolean currentInstallation;
  private String textContains;
}