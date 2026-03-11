package com.kodality.zmei.fhir.resource.diagnostics;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CarePlan extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private List<String> instantiatesCanonical;
  private List<String> instantiatesUri;
  private List<Reference> basedOn;
  private List<Reference> replaces;
  private List<Reference> partOf;
  private String status;
  private String intent;
  private List<CodeableConcept> category;
  private String title;
  private String description;
  private Reference subject;
  private Reference encounter;
  private Period period;
  private OffsetDateTime created;
  private Reference custodian;
  private List<Reference> contributor;
  private List<Reference> careTeam;
  private List<Reference> addresses;
  private List<Reference> supportingInfo;
  private List<Reference> goal;
  private List<CarePlanActivity> activity;
  private List<Annotation> note;

  public CarePlan() {
    super(ResourceType.carePlan);
  }

  @Getter
  @Setter
  public static class CarePlanActivity extends BackboneElement {
    private List<CodeableReference> performedActivity;
    private List<Annotation> progress;
    private Reference plannedActivityReference;
  }
}
