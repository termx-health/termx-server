package com.kodality.zmei.fhir.resource.general;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.MonetaryComponent;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Quantity;
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
public class ChargeItem extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private List<String> definitionUri;
  private List<String> definitionCanonical;
  private String status;
  private List<Reference> partOf;
  private CodeableConcept code;
  private Reference subject;
  private Reference encounter;
  private OffsetDateTime occurrenceDateTime;
  private Period occurrencePeriod;
  private Timing occurrenceTiming;
  private List<ChargeItemPerformer> performer;
  private Reference performingOrganization;
  private Reference requestingOrganization;
  private Reference costCenter;
  private Quantity quantity;
  private List<CodeableConcept> bodySite;
  private MonetaryComponent unitPriceComponent;
  private MonetaryComponent totalPriceComponent;
  private String overrideReason;
  private Reference enterer;
  private OffsetDateTime enteredDate;
  private List<CodeableConcept> reason;
  private List<CodeableReference> service;
  private List<CodeableReference> product;
  private List<Reference> account;
  private List<Annotation> note;
  private List<Reference> supportingInformation;

  public ChargeItem() {
    super(ResourceType.chargeItem);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ChargeItemPerformer extends BackboneElement {
    private CodeableConcept function;
    private Reference actor;
  }
}
