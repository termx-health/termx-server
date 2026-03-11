package com.kodality.zmei.fhir.resource.terminology;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ValueSet extends DomainResource implements Identifiable {
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
  private Boolean immutable;
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
  private ValueSetCompose compose;
  private ValueSetExpansion expansion;
  private ValueSetScope scope;

  public ValueSet() {
    super(ResourceType.valueSet);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetCompose extends BackboneElement {
    private LocalDate lockedDate;
    private Boolean inactive;
    private List<ValueSetComposeInclude> include;
    private List<ValueSetComposeInclude> exclude;
    private List<String> property;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetComposeInclude extends BackboneElement {
    private String system;
    private String version;
    private List<ValueSetComposeIncludeConcept> concept;
    private List<ValueSetComposeIncludeFilter> filter;
    private List<String> valueSet;
    private String copyright;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetComposeIncludeConcept extends BackboneElement {
    private String code;
    private String display;
    private List<ValueSetComposeIncludeConceptDesignation> designation;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetComposeIncludeConceptDesignation extends BackboneElement {
    private String language;
    private Coding use;
    private List<Coding> additionalUse;
    private String value;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetComposeIncludeFilter extends BackboneElement {
    private String property;
    private String op;
    private String value;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetExpansion extends BackboneElement {
    private String identifier;
    private String next;
    private OffsetDateTime timestamp;
    private Integer total;
    private Integer offset;
    private List<ValueSetExpansionProperty> property;
    private List<ValueSetExpansionParameter> parameter;
    private List<ValueSetExpansionContains> contains;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetExpansionProperty extends BackboneElement {
    private String code;
    private String uri;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetExpansionParameter extends BackboneElement {
    private String name;
    private String valueString;
    private Boolean valueBoolean;
    private Integer valueInteger;
    private BigDecimal valueDecimal;
    private String valueUri;
    private String valueCode;
    private OffsetDateTime valueDateTime;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetExpansionContains extends BackboneElement {
    private String system;
    @JsonProperty("abstract")
    private Boolean abstractField;
    private Boolean inactive;
    private String version;
    private String code;
    private String display;
    private List<ValueSetComposeIncludeConceptDesignation> designation;
    private List<ValueSetExpansionContainsProperty> property;
    private List<ValueSetExpansionContains> contains;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetExpansionContainsProperty extends BackboneElement {
    private String code;
    private String valueCode;
    private Coding valueCoding;
    private String valueString;
    private Integer valueInteger;
    private Boolean valueBoolean;
    private OffsetDateTime valueDateTime;
    private BigDecimal valueDecimal;
    private List<ValueSetExpansionContainsPropertySubProperty> subProperty;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetExpansionContainsPropertySubProperty extends BackboneElement {
    private String code;
    private String valueCode;
    private Coding valueCoding;
    private String valueString;
    private Integer valueInteger;
    private Boolean valueBoolean;
    private OffsetDateTime valueDateTime;
    private BigDecimal valueDecimal;
  }
  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ValueSetScope extends BackboneElement {
    private String inclusionCriteria;
    private String exclusionCriteria;
  }

}
