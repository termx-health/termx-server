package com.kodality.zmei.fhir;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class BackboneElement extends Element {
  private List<Extension> modifierExtension;
}
