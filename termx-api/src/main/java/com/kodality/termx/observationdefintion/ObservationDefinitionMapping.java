package com.kodality.termx.observationdefintion;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinitionMapping {
  private Long id;
  private ObservationDefinitionMappingTarget target;
  private int orderNumber;
  private String mapSet;
  private String codeSystem;
  private String concept;
  private String relation;
  private String condition;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ObservationDefinitionMappingTarget {
    private Long id;
    private String type;
  }
}
