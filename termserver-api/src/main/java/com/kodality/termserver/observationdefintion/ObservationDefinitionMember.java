package com.kodality.termserver.observationdefintion;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.LocalizedName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinitionMember {
  private Long id;
  private CodeName item;
  private LocalizedName names;
  private int orderNumber;
  private ObservationDefinitionCardinality cardinality;
}
