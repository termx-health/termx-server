package com.kodality.zmei.fhir.resource.terminology;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.ContactDetail;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.RelatedArtifact;
import com.kodality.zmei.fhir.datatypes.UsageContext;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystem extends DomainResource implements Identifiable {
  private String url;
  private List<Identifier> identifier;
  private String version;
  private String versionAlgorithmString;
  private Coding versionAlgorithmCoding;
  private String name;
  private String title;
  private String status;
  private Boolean experimental;
  private OffsetDateTime date;
  private String publisher;
  private List<ContactDetail> contact;
  private String description;
  private List<UsageContext> useContext;
  private List<CodeableConcept> jurisdiction;
  private String purpose;
  private String copyright;
  private String copyrightLabel;
  private LocalDate approvalDate;
  private LocalDate lastReviewDate;
  private Period effectivePeriod;
  private CodeableConcept topic;
  private List<ContactDetail> author;
  private List<ContactDetail> editor;
  private List<ContactDetail> reviewer;
  private List<ContactDetail> endorser;
  private List<RelatedArtifact> relatedArtifact;
  private Boolean caseSensitive;
  private String valueSet;
  private String hierarchyMeaning;
  private Boolean compositional;
  private Boolean versionNeeded;
  private String content;
  private String supplements;
  private Integer count;
  private List<CodeSystemFilter> filter;
  private List<CodeSystemProperty> property;
  private List<CodeSystemConcept> concept;


  public CodeSystem() {
    super(ResourceType.codeSystem);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemFilter extends BackboneElement {
    private String code;
    private String description;
    private List<String> operator;
    private String value;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemProperty extends BackboneElement {
    private String code;
    private String uri;
    private String description;
    private String type;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemConcept extends BackboneElement {
    private String code;
    private String display;
    private String definition;
    private List<CodeSystemConceptDesignation> designation;
    private List<CodeSystemConceptProperty> property;
    private List<CodeSystemConcept> concept;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemConceptDesignation extends BackboneElement {
    private String language;
    private Coding use;
    private List<Coding> additionalUse;
    private String value;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CodeSystemConceptProperty extends BackboneElement {
    private String code;
    private String valueCode;
    private Coding valueCoding;
    private String valueString;
    private Integer valueInteger;
    private Boolean valueBoolean;
    private OffsetDateTime valueDateTime;
    private BigDecimal valueDecimal;
  }

}
