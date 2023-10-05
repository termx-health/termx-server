package com.kodality.termx.observationdefintion;

import com.kodality.commons.model.QueryParams;
import java.util.List;
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
  private String categories; //codeSystem|code,codeSystem|code
  private String structures;
  private String types;

  private boolean decorated;
  private boolean decoratedValue;

  private List<Long> permittedIds;
}
