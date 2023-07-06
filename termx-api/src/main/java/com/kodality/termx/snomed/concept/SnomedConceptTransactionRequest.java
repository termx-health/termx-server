package com.kodality.termx.snomed.concept;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedConceptTransactionRequest {
  private Map<String, SnomedConceptTransactionContent> concepts;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SnomedConceptTransactionContent {
    private List<Long> translationIds;
  }
}
