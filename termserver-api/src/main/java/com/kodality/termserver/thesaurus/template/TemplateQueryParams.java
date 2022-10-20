package com.kodality.termserver.thesaurus.template;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TemplateQueryParams extends QueryParams {
  private String textContains;
}
