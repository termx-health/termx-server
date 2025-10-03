package com.kodality.termx.implementationguide.ig;

import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersion;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ImplementationGuideTransactionRequest {
  @Valid
  private ImplementationGuide implementationGuide;
  private ImplementationGuideVersion version;
}
