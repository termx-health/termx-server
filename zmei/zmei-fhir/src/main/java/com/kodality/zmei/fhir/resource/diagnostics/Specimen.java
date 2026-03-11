package com.kodality.zmei.fhir.resource.diagnostics;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Duration;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Quantity;
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
public class Specimen extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private Identifier accessionIdentifier;
  private String status;
  private CodeableConcept type;
  private Reference subject;
  private OffsetDateTime receivedTime;
  private Reference parent;
  private Reference request;
  private String combined;
  private CodeableConcept role;
  private List<SpecimenFeature> feature;
  private SpecimenCollection collection;
  private List<SpecimenProcessing> processing;
  private List<SpecimenContainer> container;
  private List<CodeableConcept> condition;
  private List<Annotation> note;

  public Specimen() {
    super(ResourceType.specimen);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SpecimenFeature extends BackboneElement {
    private CodeableConcept type;
    private String description;
  }
  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SpecimenCollection extends BackboneElement {
    private Reference collector;
    private OffsetDateTime collectedDateTime;
    private Period collectedPeriod;
    private Duration duration;
    private Quantity quantity;
    private CodeableConcept method;
    private CodeableReference device;
    private Reference procedure;
    private CodeableReference bodySite;
    private CodeableConcept fastingStatusCodeableConcept;
    private Duration fastingStatusDuration;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SpecimenProcessing extends BackboneElement {
    private String description;
    private CodeableConcept method;
    private Reference additive;
    private OffsetDateTime timeDateTime;
    private Period timePeriod;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SpecimenContainer extends BackboneElement {
    private Reference device;
    private Reference location;
    private Quantity specimenQuantity;
  }
}
