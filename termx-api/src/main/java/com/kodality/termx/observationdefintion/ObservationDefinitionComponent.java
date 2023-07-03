package com.kodality.termx.observationdefintion;

import com.kodality.commons.model.LocalizedName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinitionComponent {
  private Long id;
  private String sectionType;
  private String code;
  private LocalizedName names;
  private int orderNumber;
  private ObservationDefinitionCardinality cardinality;
  private String type;
  private ObservationDefinitionUnit unit;
  private String valueSet;
}
