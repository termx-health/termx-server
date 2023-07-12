package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TransformationDefinitionQueryParams extends QueryParams {
  private String nameContains;
}