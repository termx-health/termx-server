package com.kodality.zmei.fhir.resource.servicerequest;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Quantity;
import com.kodality.zmei.fhir.datatypes.Range;
import com.kodality.zmei.fhir.datatypes.Ratio;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.Timing;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.util.Lists;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ServiceRequest extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private List<String> instantiatesCanonical;
  private List<String> instantiatesUri;
  private List<Reference> basedOn;
  private List<Reference> replaces;
  private Identifier requisition;
  private String status;
  private String intent;
  private List<CodeableConcept> category;
  private String priority;
  private Boolean doNotPerform;
  private CodeableReference code;
  private List<ServiceRequestOrderDetail> orderDetail;
  private Quantity quantityQuantity;
  private Ratio quantityRatio;
  private Range quantityRange;
  private Reference subject;
  private List<Reference> focus;
  private Reference encounter;
  private OffsetDateTime occurrenceDateTime;
  private Period occurrencePeriod;
  private Timing occurrenceTiming;
  private Boolean asNeededBoolean;
  private CodeableConcept asNeededCodeableConcept;
  private OffsetDateTime authoredOn;
  private Reference requester;
  private CodeableConcept performerType;
  private List<Reference> performer;
  private List<CodeableReference> location;
  private List<CodeableReference> reason;
  private List<Reference> insurance;
  private List<CodeableReference> supportingInfo;
  private List<Reference> specimen;
  private List<CodeableConcept> bodySite;
  private List<Annotation> note;
  private List<ServiceRequestPatientInstruction> patientInstruction;
  private List<Reference> relevantHistory;

  public ServiceRequest() {
    super(ResourceType.serviceRequest);
  }

  public ServiceRequest addBasedOn(Reference o) {
    this.basedOn = Lists.add(this.basedOn, o);
    return this;
  }

  public ServiceRequest addReplaces(Reference o) {
    this.replaces = Lists.add(this.replaces, o);
    return this;
  }

  public ServiceRequest addCategory(CodeableConcept o) {
    this.category = Lists.add(this.category, o);
    return this;
  }

  public ServiceRequest addOrderDetail(ServiceRequestOrderDetail o) {
    this.orderDetail = Lists.add(this.orderDetail, o);
    return this;
  }

  public ServiceRequest addPerformer(Reference o) {
    this.performer = Lists.add(this.performer, o);
    return this;
  }

  public ServiceRequest addLocation(CodeableReference o) {
    this.location = Lists.add(this.location, o);
    return this;
  }

  public ServiceRequest addReason(CodeableReference o) {
    this.reason = Lists.add(this.reason, o);
    return this;
  }

  public ServiceRequest addInsurance(Reference o) {
    this.insurance = Lists.add(this.insurance, o);
    return this;
  }

  public ServiceRequest addSupportingInfo(CodeableReference o) {
    this.supportingInfo = Lists.add(this.supportingInfo, o);
    return this;
  }

  public ServiceRequest addSpecimen(Reference o) {
    this.specimen = Lists.add(this.specimen, o);
    return this;
  }

  public ServiceRequest addBodySite(CodeableConcept o) {
    this.bodySite = Lists.add(this.bodySite, o);
    return this;
  }

  public ServiceRequest addNote(Annotation o) {
    this.note = Lists.add(this.note, o);
    return this;
  }

  public ServiceRequest addRelevantHistory(Reference o) {
    this.relevantHistory = Lists.add(this.relevantHistory, o);
    return this;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ServiceRequestOrderDetail extends BackboneElement {
    private CodeableReference paramtereFocus;
    private List<ServiceRequestOrderDetailParameter> parameter;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ServiceRequestOrderDetailParameter extends BackboneElement {
    private CodeableConcept code;
    private Quantity valueQuantity;
    private Ratio valueRatio;
    private Range valueRange;
    private Boolean valueBoolean;
    private CodeableConcept valueCodeableConcept;
    private String valueString;
    private Period valuePeriod;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ServiceRequestPatientInstruction extends BackboneElement {
    private String instructionMarkdown;
    private Reference instructionReference;
  }
}
