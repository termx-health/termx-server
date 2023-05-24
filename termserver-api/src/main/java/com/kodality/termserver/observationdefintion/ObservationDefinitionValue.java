package com.kodality.termserver.observationdefintion;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinitionValue {
  private Long id;
  private String behaviour;
  private String expression;
  private String type;
  private ObservationDefinitionUnit unit;
  private boolean multipleResultsAllowed;

  private String usage;
  private List<ObservationDefinitionValueItem> values;
  private String valueSet;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ObservationDefinitionValueItem {
    private String codeSystem;
    private String code;
  }
}
