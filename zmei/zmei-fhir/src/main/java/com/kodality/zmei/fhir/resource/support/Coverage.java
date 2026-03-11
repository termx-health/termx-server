package com.kodality.zmei.fhir.resource.support;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Money;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Quantity;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.util.Lists;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Coverage extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private String status;
  private String kind;
  private List<CoveragePaymentBy> paymentBy;
  private CodeableConcept type;
  private Reference policyHolder;
  private Reference subscriber;
  private List<Identifier> subscriberId;
  private Reference beneficiary;
  private String dependent;
  private CodeableConcept relationship;
  private Period period;
  private Reference issuer;
  @JsonProperty("class")
  private CoverageClass clazz;
  private Integer order;
  private List<CoverageCostToBeneficiary> costToBeneficiary;
  private String network;
  private Boolean subrogation;
  private Reference contract;
  private Reference insurancePlan;

  public Coverage() {
    super(ResourceType.coverage);
  }

  public Coverage addIdentifier(Identifier o) {
    this.identifier = Lists.add(this.identifier, o);
    return this;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CoveragePaymentBy extends BackboneElement {
    private Reference party;
    private String responsibility;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CoverageClass extends BackboneElement {
    private CodeableConcept type;
    private Identifier value;
    private String name;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CoverageCostToBeneficiary extends BackboneElement {
    private CodeableConcept type;
    private CodeableConcept category;
    private CodeableConcept network;
    private CodeableConcept unit;
    private CodeableConcept term;
    private Quantity valueQuantity;
    private Money valueMoney;
    private List<CoverageCostToBeneficiaryException> exception;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CoverageCostToBeneficiaryException extends BackboneElement {
    private CodeableConcept type;
    private Period period;
  }
}
