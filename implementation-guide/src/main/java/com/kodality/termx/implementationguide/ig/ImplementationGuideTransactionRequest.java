package com.kodality.termx.implementationguide.ig;

import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersion;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ImplementationGuideTransactionRequest {
  private ImplementationGuide implementationGuide;
  private ImplementationGuideVersion version;
}
