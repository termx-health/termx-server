package com.kodality.zmei.fhir.resource.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Duration;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.VirtualServiceDetail;
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
public class Encounter extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private String status;
  @JsonProperty("class")
  private List<CodeableConcept> clazz;
  private CodeableConcept priority;
  private List<CodeableConcept> type;
  private List<CodeableReference> serviceType;
  private Reference subject;
  private CodeableConcept subjectStatus;
  private List<Reference> episodeOfCare;
  private List<Reference> basedOn;
  private List<Reference> careTeam;
  private Reference partOf;
  private Reference serviceProvider;
  private List<EncounterParticipant> participant;
  private List<Reference> appointment;
  private VirtualServiceDetail virtualService;
  private Period actualPeriod;
  private OffsetDateTime plannedStartDate;
  private OffsetDateTime plannedEndDate;
  private Duration length;
  private List<EncounterReason> reason;
  private List<EncounterDiagnosis> diagnosis;
  private List<Reference> account;
  private List<CodeableConcept> dietPreference;
  private List<CodeableConcept> specialArrangement;
  private List<CodeableConcept> specialCourtesy;
  private EncounterAdmission admission;
  private List<EncounterLocation> location;

  public Encounter() {
    super(ResourceType.encounter);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class EncounterParticipant extends BackboneElement {
    private List<CodeableConcept> type;
    private Period period;
    private Reference actor;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class EncounterReason extends BackboneElement {
    private List<CodeableConcept> use;
    private CodeableReference value;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class EncounterDiagnosis extends BackboneElement {
    private List<CodeableReference> condition;
    private List<CodeableConcept> use;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class EncounterHospitalization extends BackboneElement {
    private Identifier preAdmissionIdentifier;
    private Reference origin;
    private CodeableConcept adminSource;
    private CodeableConcept reAdmission;
    private List<CodeableConcept> dietPreference;
    private List<CodeableConcept> specialCourtesy;
    private CodeableConcept specialArrangement;
    private Reference destination;
    private CodeableConcept dischargeDisposition;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class EncounterAdmission extends BackboneElement {
    private Identifier preAdmissionIdentifier;
    private Reference origin;
    private CodeableConcept admitSource;
    private CodeableConcept reAdmission;
    private Reference destination;
    private CodeableConcept dischargeDisposition;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class EncounterLocation extends BackboneElement {
    private Reference location;
    private String status;
    private CodeableConcept form;
    private Period period;
  }

}
