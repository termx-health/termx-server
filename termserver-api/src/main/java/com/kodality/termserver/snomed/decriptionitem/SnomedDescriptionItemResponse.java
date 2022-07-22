package com.kodality.termserver.snomed.decriptionitem;

import com.kodality.termserver.snomed.concept.SnomedConcept;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedDescriptionItemResponse {
  private SnomedBuckets buckets;
  private List<SnomedDescriptionItem> items;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SnomedBuckets {
    private Map<String, Long> module;
    private Map<String, Long> semanticTags;
    private Map<String, Long> language;
    private Map<String, String> languageNames;
    private Map<String, Long> membership;
    private Map<String, SnomedConcept> bucketConcepts;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SnomedDescriptionItem {
    private String term;
    private Boolean active;
    private SnomedConcept concept;
  }
}
