package com.kodality.termserver.loinc.utils;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class LoincConcept {
  private String code;
  private String display;
  private List<LoincConceptProperty> properties;
  private List<LoincConceptAssociation> associations;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class LoincConceptProperty {
    private String name;
    private String type;
    private Object value;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class LoincConceptAssociation {
    private String targetCode;
    private Integer order;
  }
}
