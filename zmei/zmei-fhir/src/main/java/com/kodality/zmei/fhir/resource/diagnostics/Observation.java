package com.kodality.zmei.fhir.resource.diagnostics;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Quantity;
import com.kodality.zmei.fhir.datatypes.Range;
import com.kodality.zmei.fhir.datatypes.Ratio;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.Timing;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Observation extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private String instantiatesCanonical;
  private Reference instantiatesReference;
  private List<Reference> basedOn;
  private List<ObservationTriggeredBy> triggeredBy;
  private List<Reference> partOf;
  private String status;
  private List<CodeableConcept> category;
  private CodeableConcept code;
  private Reference subject;
  private List<Reference> focus;
  private Reference encounter;
  private OffsetDateTime effectiveDateTime;
  private Period effectivePeriod;
  private Timing effectiveTiming;
  private OffsetDateTime effectiveInstant;
  private OffsetDateTime issued;
  private List<Reference> performer;
  private Quantity valueQuantity;
  private CodeableConcept valueCodeableConcept;
  private String valueString;
  private Boolean valueBoolean;
  private Integer valueInteger;
  private Range valueRange;
  private Ratio valueRatio;
  //TODO: valueSampledData
  private LocalTime valueTime;
  private OffsetDateTime valueDateTime;
  private Period valuePeriod;
  private Attachment valueAttachment;
  private CodeableConcept dataAbsentReason;
  private List<CodeableConcept> interpretation;
  private List<Annotation> note;
  private CodeableConcept bodySite;
  private Reference bodyStructure;
  private CodeableConcept method;
  private Reference specimen;
  private Reference device;
  private List<ObservationReferenceRange> referenceRange;
  private List<Reference> hasMember;
  private List<Reference> derivedFrom;
  private List<ObservationComponent> component;

  public Observation() {
    super(ResourceType.observation);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ObservationTriggeredBy extends BackboneElement {
    private Reference observation;
    private String type;
    private String reason;
  }
  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ObservationReferenceRange extends BackboneElement {
    private Quantity low;
    private Quantity high;
    private CodeableConcept normalValue;
    private CodeableConcept type;
    private CodeableConcept appliesTo;
    private Range age;
    private String text;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ObservationComponent extends BackboneElement {
    private CodeableConcept code;
    private Quantity valueQuantity;
    private CodeableConcept valueCodeableConcept;
    private String valueString;
    private Boolean valueBoolean;
    private Integer valueInteger;
    private Range valueRange;
    private Ratio valueRatio;
    private LocalTime valueTime;
    private OffsetDateTime valueDateTime;
    private Period valuePeriod;
    private Attachment valueAttachment;

    private CodeableConcept dataAbsentReason;
    private List<CodeableConcept> interpretation;
    private List<ObservationReferenceRange> referenceRange;
  }
}
