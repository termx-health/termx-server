package com.kodality.termserver.codesystem;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemQueryParams extends QueryParams {
  private String id;
  private String idContains;
  private String uri;
  private String uriContains;
  private String name;
  private String nameContains;
  private String description;
  private String descriptionContains;

  private Long codeSystemEntityVersionId;

  private boolean decorated;
}
