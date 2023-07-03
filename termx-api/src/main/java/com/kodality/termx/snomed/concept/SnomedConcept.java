package com.kodality.termx.snomed.concept;

import com.kodality.termx.snomed.description.SnomedDescription;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedConcept {
  private String id;
  private String conceptId;
  private String moduleId;
  private String effectiveTime;
  private String definitionStatus;
  private SnomedConceptName fsn;
  private SnomedConceptName pt;
  private boolean active;
  private List<SnomedDescription> descriptions;
  private List<SnomedRelationship> relationships;
  private Boolean isLeafInferred;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SnomedConceptName {
    private String term;
    private String lang;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SnomedRelationship {
    private boolean active;
    private String destinationId;
    private String effectiveTime;
    private Long groupId;
    private String id;
    private String moduleId;
    private String relationshipId;
    private boolean released;
    private String sourceId;
    private SnomedConcept target;
    private SnomedConcept type;
    private String typeId;
  }
}
