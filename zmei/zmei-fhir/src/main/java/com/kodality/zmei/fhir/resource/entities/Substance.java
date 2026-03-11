package com.kodality.zmei.fhir.resource.entities;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
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
public class Substance extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private Boolean instance;
  private String status;
  private List<CodeableConcept> category;
  private CodeableConcept code;
  private String description;
  private OffsetDateTime expirity;
  private Quantity quantity;
  private List<SubstanceIngredient> ingredient;

  public Substance() {
    super(ResourceType.substance);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class SubstanceIngredient extends BackboneElement {
    private Ratio quantity;
    private CodeableConcept substanceCodeableConcept;
    private Reference substanceReference;
  }
}
