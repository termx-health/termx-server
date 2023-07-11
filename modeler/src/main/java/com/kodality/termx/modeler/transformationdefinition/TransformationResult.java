package com.kodality.termx.modeler.transformationdefinition;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TransformationResult {
  private String result;
  private String error;
}


