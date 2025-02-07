package com.kodality.termx.snomed.refset;

import com.kodality.termx.snomed.concept.SnomedConcept;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedRefsetResponse {
  private Map<String, Long> memberCountsByReferenceSet;
  private Map<String, SnomedConcept> referenceSets;
  private List<SnomedRefsetItem> items;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SnomedRefsetItem{
    private boolean active;
    private String moduleId;
    private String refsetId;
    private String referencedComponentId;
    private Map<String, Object> referencedComponent;
    private SnomedRefsetAdditionalFields additionalFields;

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class SnomedRefsetAdditionalFields{
      private String mapTarget;
    }
  }
}
