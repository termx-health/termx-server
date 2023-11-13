package com.kodality.termx.implementationguide.ig.version;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ImplementationGuideVersionQueryParams extends QueryParams {
  private String implementationGuideIds;

  private List<String> permittedImplementationGuideIds;
}
