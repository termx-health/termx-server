package com.kodality.zmei.fhir.resource.billing;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.MonetaryComponent;
import com.kodality.zmei.fhir.datatypes.Money;
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
public class Invoice extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private String status;
  private String cancelledReason;
  private CodeableConcept type;
  private Reference subject;
  private Reference recipient;
  @Deprecated
  private OffsetDateTime date;
  private OffsetDateTime creation;
  private OffsetDateTime periodDate;
  private Period periodPeriod;
  private List<InvoiceParticipant> participant;
  private Reference issuer;
  private Reference account;
  private List<InvoiceLineItem> lineItem;
  private List<MonetaryComponent> totalPriceComponent;
  private Money totalNet;
  private Money totalGross;
  private String paymentTerms;
  private List<Annotation> note;

  public Invoice() {
    super(ResourceType.invoice);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class InvoiceParticipant extends BackboneElement {
    private CodeableConcept role;
    private Reference actor;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class InvoiceLineItem extends BackboneElement {
    private Integer sequence;
    private OffsetDateTime servedDate;
    private Period servedPeriod;
    private Reference chargeItemReference;
    private CodeableConcept chargeItemCodeableConcept;
    private List<MonetaryComponent> priceComponent;
  }

}
