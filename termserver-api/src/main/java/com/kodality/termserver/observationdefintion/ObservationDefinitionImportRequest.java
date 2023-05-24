package com.kodality.termserver.observationdefintion;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinitionImportRequest {
  private List<String> loincCodes;
}
