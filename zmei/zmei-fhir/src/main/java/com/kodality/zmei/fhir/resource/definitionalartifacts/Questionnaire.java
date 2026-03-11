package com.kodality.zmei.fhir.resource.definitionalartifacts;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.ContactDetail;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Quantity;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.UsageContext;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import java.math.BigDecimal;
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
public class Questionnaire extends DomainResource implements Identifiable {
  private String url;
  private List<Identifier> identifier;
  private String version;
  private String versionAlgorithmString;
  private Coding versionAlgorithmCoding;
  private String name;
  private String title;
  private String derivedFrom;
  private String status;
  private Boolean experimental;
  private String subjectType;
  private OffsetDateTime date;
  private String publisher;
  private List<ContactDetail> contact;
  private String description;
  private List<UsageContext> useContext;
  private String purpose;
  private String copyright;
  private String copyrightLabel;
  private OffsetDateTime approvalDate;
  private OffsetDateTime lastReviewDate;
  private Period effectivePeriod;
  private Coding code;
  private List<QuestionnaireItem> item;

  public Questionnaire() {
    super(ResourceType.questionnaire);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class QuestionnaireItem extends BackboneElement {
    private String linkId;
    private String definition;
    private Coding code;
    private String prefix;
    private String text;
    private String type;
    private List<QuestionnaireItemEnabledWhen> enabledWhen;
    private String enableBehavior;
    private String disavledDisplay;
    private Boolean required;
    private Boolean repeats;
    private Boolean readOnly;
    private Integer maxLength;
    private String answerConstraint;
    private String answerValueSet;
    private List<QuestionnaireItemAnswerOption> answerOption;
    private List<QuestionnaireItem> item;

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class QuestionnaireItemEnabledWhen extends BackboneElement {
      private String question;
      private String operator;
      private Boolean answerBoolean;
      private BigDecimal answerDecimal;
      private Integer answerInteger;
      private OffsetDateTime answerDate;
      private OffsetDateTime answerDateTime;
      private LocalTime answerTime;
      private String answerString;
      private Coding answerCoding;
      private Quantity answerQuantity;
      private Reference answerReference;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    public static class QuestionnaireItemAnswerOption extends BackboneElement {
      private Integer valueInteger;
      private LocalDate valueDate;
      private LocalTime valueTime;
      private String valueString;
      private Coding valueCoding;
      private Reference valueReference;
      private Boolean initialSelected;
      private List<QuestionnaireItemAnswerOptionInitial> initial;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    private static class QuestionnaireItemAnswerOptionInitial  extends BackboneElement {
      private Boolean valueBoolean;
      private BigDecimal valueDecimal;
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
    }
  }
}
