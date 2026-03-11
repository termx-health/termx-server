package com.kodality.zmei.fhir.resource.request;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Quantity;
import com.kodality.zmei.fhir.datatypes.Range;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.Timing;
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
public class DeviceRequest extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private List<String> instantiatesCanonical;
  private List<String> instantiatesUri;
  private List<Reference> basedOn;
  private List<Reference> replaces;
  private Identifier groupIdentifier;
  private String status;
  private String intent;
  private String priority;
  private Boolean doNotPerform;
  private CodeableReference code;
  private Integer quantity;
  private List<DeviceRequestParameter> parameter;
  private Reference subject;
  private Reference encounter;
  private OffsetDateTime occurrenceDateTime;
  private Period occurrencePeriod;
  private Timing occurrenceTiming;
  private OffsetDateTime authoredOn;
  private Reference requester;
  private CodeableReference performer;
  private List<CodeableReference> reason;
  private Boolean asNeeded;
  private CodeableConcept asNeededFor;
  private List<Reference> insurance;
  private List<Reference> supportingInfo;
  private List<Annotation> note;
  private List<Reference> relevantHistory;

  public DeviceRequest() {
    super(ResourceType.deviceRequest);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DeviceRequestParameter extends BackboneElement {
    private CodeableConcept code;
    private CodeableConcept valueCodeableConcept;
    private Quantity valueQuantity;
    private Range valueRange;
    private Boolean valueBoolean;
  }
}
