package com.kodality.termserver.observationdefintion;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinitionSearchParams extends QueryParams {
  private String codes;
  private String idsNe;
  private String textContains;
  private String types;

  private boolean decorated;
  private boolean decoratedValue;
}
