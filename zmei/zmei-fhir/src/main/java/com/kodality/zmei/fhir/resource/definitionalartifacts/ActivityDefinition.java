package com.kodality.zmei.fhir.resource.definitionalartifacts;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Age;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.ContactDetail;
import com.kodality.zmei.fhir.datatypes.Duration;
import com.kodality.zmei.fhir.datatypes.Expression;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Quantity;
import com.kodality.zmei.fhir.datatypes.Range;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.RelatedArtifact;
import com.kodality.zmei.fhir.datatypes.Timing;
import com.kodality.zmei.fhir.datatypes.UsageContext;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.resource.medications.Dosage;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ActivityDefinition extends DomainResource implements Identifiable {
  private String url;
  private List<Identifier> identifier;
  private String version;
  private String versionAlgorithmString;
  private Coding versionAlgorithmCoding;
  private String name;
  private String title;
  private String subtitle;
  private String status;
  private Boolean experimental;
  private CodeableConcept subjectCodeableConcept;
  private Reference subjectReference;
  private String subjectCanonical;
  private OffsetDateTime date;
  private String publisher;
  private List<ContactDetail> contact;
  private String description;
  private List<UsageContext> useContext;
  private String purpose;
  private String usage;
  private String copyright;
  private String copyrightLabel;
  private LocalDate approvalDate;
  private LocalDate lastReviewDate;
  private Period effectivePeriod;
  private List<ContactDetail> author;
  private List<ContactDetail> editor;
  private List<ContactDetail> reviewer;
  private List<ContactDetail> endorser;
  private List<RelatedArtifact> relatedArtifact;
  private List<String> library;
  private String kind;
  private String profile;
  private CodeableConcept code;
  private String intent;
  private String priority;
  private Boolean doNotPerform;
  private Timing timingTiming;
  private Age timingAge;
  private Range timingRange;
  private Duration timingDuration;
  private Boolean asNeededBoolean;
  private CodeableConcept asNeededCodeableConcept;
  private CodeableReference location;
  private List<ActivityDefinitionParticipant> participant;
  private Reference productReference;
  private CodeableConcept productCodeableConcept;
  private Quantity quantity;
  private List<Dosage> dosage;
  private List<CodeableConcept> bodySite;
  private List<String> specimenRequirement;
  private List<String> observationRequirement;
  private List<String> observationResultRequirement;
  private String transform;
  private List<ActivityDefinitionDynamicValue> dynamicValue;

  public ActivityDefinition() {
    super(ResourceType.activityDefinition);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ActivityDefinitionParticipant extends BackboneElement {
    private String type;
    private String typeCanonical;
    private Reference typeReference;
    private CodeableConcept role;
    private CodeableConcept function;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ActivityDefinitionDynamicValue extends BackboneElement {
    private String path;
    private Expression expression;
  }
}
