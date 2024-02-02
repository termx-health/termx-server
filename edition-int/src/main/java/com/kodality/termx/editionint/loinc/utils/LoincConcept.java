package com.kodality.termx.editionint.loinc.utils;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.tuple.Pair;

@Getter
@Setter
@Accessors(chain = true)
public class LoincConcept {
  private String code;
  private Map<String, String> display;
  private List<Pair<String, String>> relatedNames;
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
