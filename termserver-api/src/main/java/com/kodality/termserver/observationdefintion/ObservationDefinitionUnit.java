package com.kodality.termserver.observationdefintion;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinitionUnit {
  private String system;
  private String unit;
}
