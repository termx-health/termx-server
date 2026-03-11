package com.kodality.zmei.fhir.resource.medications;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Identifier;
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
public class ImmunizationRecommendation extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private Reference patient;
  private OffsetDateTime date;
  private Reference authority;
  private List<ImmunizationRecommendationRecommendation> recommendation;

  public ImmunizationRecommendation() {
    super(ResourceType.immunizationRecommendation);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ImmunizationRecommendationRecommendation extends BackboneElement {
    private List<CodeableConcept> vaccineCode;
    private List<CodeableConcept> targetDisease;
    private List<CodeableConcept> contraindicatedVaccineCode;
    private CodeableConcept forecastStatus;
    private List<CodeableConcept> forecastReason;
    private List<ImmunizationRecommendationDateCriterion> dateCriterion;
    private String description;
    private String series;
    private String doseNumber;
    private String seriesDose;
    private List<Reference> supportingImmunization;
    private List<Reference> supportingPatientInformation;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ImmunizationRecommendationDateCriterion extends BackboneElement {
    private CodeableConcept code;
    private OffsetDateTime value;
  }
}
