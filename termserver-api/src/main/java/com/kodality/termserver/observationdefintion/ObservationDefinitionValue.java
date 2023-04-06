package com.kodality.termserver.observationdefintion;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinitionValue {
  private Long id;
  private String behaviour;
  private String expression;
  private String type;
  private ObservationDefinitionUnit unit;
  private String valueSet;
  private boolean multipleResultsAllowed;
}
