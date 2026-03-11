package com.kodality.zmei.fhir.resource.management;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class EpisodeOfCare extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private String status;
  private List<EpisodeOfCareStatusHistory> statusHistory;
  private List<CodeableConcept> type;
  private List<EpisodeOfCareReason> reason;
  private List<EpisodeOfCareDiagnosis> diagnosis;
  private Reference patient;
  private Reference managingOrganization;
  private Period period;
  private List<Reference> referralRequest;
  private Reference careManager;
  private List<Reference> team;
  private List<Reference> account;

  public EpisodeOfCare() {
    super(ResourceType.episodeOfCare);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class EpisodeOfCareStatusHistory extends BackboneElement {
    private String status;
    private Period period;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class EpisodeOfCareReason extends BackboneElement {
    private CodeableConcept use;
    private CodeableReference value;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class EpisodeOfCareDiagnosis extends BackboneElement {
    private List<CodeableReference> condition;
    private CodeableConcept use;
  }
}
