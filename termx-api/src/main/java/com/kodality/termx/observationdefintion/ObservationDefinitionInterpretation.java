package com.kodality.termx.observationdefintion;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinitionInterpretation {
  private Long id;
  private ObservationDefinitionInterpretationTarget target;
  private int orderNumber;
  private ObservationDefinitionInterpretationState state;
  private ObservationDefinitionInterpretationRange range;
  private String condition;
  private String rangeCategory;
  private String fhirInterpretationCode;
  private String snomedInterpretationCode;


  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ObservationDefinitionInterpretationTarget {
    private String type;
    private Long id;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ObservationDefinitionInterpretationState {
    private String gender;
    private ObservationDefinitionCardinality age;
    private ObservationDefinitionCardinality gestationAge;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ObservationDefinitionInterpretationRange {
    private ObservationDefinitionCardinality numericRange;
    private String codeSystem;
    private List<String> codeSystemConcepts;
    private String valueSet;

  }
}
