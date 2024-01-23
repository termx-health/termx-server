package com.kodality.termx.sys.checklist;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ChecklistAssertionQueryParams extends QueryParams {
  private String checklistResource; //type|id
}
