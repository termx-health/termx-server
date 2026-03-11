package com.kodality.zmei.fhir.resource.diagnostics;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Quantity;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.util.Lists;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class QuestionnaireResponse extends DomainResource {
  private Identifier identifier;
  private List<Reference> basedOn;
  private List<Reference> partOf;
  private String questionnaire;
  private String status;
  private Reference subject;
  private Reference encounter;
  private OffsetDateTime authored;
  private Reference author;
  private Reference source;
  private List<QuestionnaireResponseItem> item;

  public QuestionnaireResponse() {
    super(ResourceType.questionnaireResponse);
  }

  public QuestionnaireResponse addBasedOn(Reference o) {
    this.basedOn = Lists.add(this.basedOn, o);
    return this;
  }

  public QuestionnaireResponse addPartOf(Reference o) {
    this.partOf = Lists.add(this.partOf, o);
    return this;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class QuestionnaireResponseItem extends BackboneElement {
    private String linkId;
    private String definition;
    private String text;
    private List<QuestionnaireResponseItemAnswer> answer;
    private List<QuestionnaireResponseItem> item;

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class QuestionnaireResponseItemAnswer extends BackboneElement {
      private Boolean valueBoolean;
      private Double valueDecimal;
      private Integer valueInteger;
      private LocalDate valueDate;
      private OffsetDateTime valueDateTime;
      private LocalTime valueTime;
      private String valueString;
      private String valueUri;
      private Attachment valueAttachment;
      private Coding valueCoding;
      private Quantity valueQuantity;
      private Reference valueReference;
      private List<QuestionnaireResponseItem> item;
    }
  }
}
