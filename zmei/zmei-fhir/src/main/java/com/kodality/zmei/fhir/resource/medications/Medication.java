package com.kodality.zmei.fhir.resource.medications;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Quantity;
import com.kodality.zmei.fhir.datatypes.Ratio;
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
public class Medication extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private CodeableConcept code;
  private String status;
  private Reference marketingAuthorizationHolder;
  private CodeableConcept doseForm;
  private Quantity totalVolume;
  private List<MedicationIngredient> ingredient;
  private MedicationBatch batch;
  private Reference definition;

  public Medication() {
    super(ResourceType.medication);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class MedicationIngredient extends BackboneElement {
    private CodeableReference item;
    @JsonProperty(value = "isActive")
    private Boolean active;
    private Ratio strengthRatio;
    private CodeableConcept strengthCodeableConcept;
    private Quantity strengthQuantity;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  private static class MedicationBatch extends BackboneElement {
    private String lotNumber;
    private OffsetDateTime expirationDate;
  }
}
