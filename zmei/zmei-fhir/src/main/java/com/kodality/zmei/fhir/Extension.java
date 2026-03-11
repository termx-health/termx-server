package com.kodality.zmei.fhir;

import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Money;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.RelatedArtifact;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Extension extends Element {
  private String url;

  private String valueString;
  private String valueCode;
  private Integer valueInteger;
  private BigDecimal valueDecimal;
  private OffsetDateTime valueDateTime;
  private LocalDate valueDate;
  private Boolean valueBoolean;
  private Reference valueReference;
  private Identifier valueIdentifier;
  private CodeableConcept valueCodeableConcept;
  private Money valueMoney;
  private Attachment valueAttachment;
  private String valueUrl;
  private String valueUri;
  private RelatedArtifact valueRelatedArtifact;
  private String valueCanonical;


  public Extension() {
    //
  }

  public Extension(String url) {
    this.url = url;
  }
}
