package com.kodality.zmei.fhir.resource.terminology;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.ContactDetail;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Quantity;
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
public class ConceptMap extends DomainResource implements Identifiable {
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
  private List<ConceptMapProperty> property;
  private String sourceScopeUri;
  private String sourceScopeCanonical;
  private String targetScopeUri;
  private String targetScopeCanonical;
  private List<ConceptMapGroup> group;

  public ConceptMap() {
    super(ResourceType.conceptMap);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConceptMapProperty extends BackboneElement {
    private String code;
    private String uri;
    private String description;
    private String type;
    private String system;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConceptMapGroup extends BackboneElement {
    private String source;
    private String target;
    private List<ConceptMapGroupElement> element;
    private ConceptMapGroupUnmapped unmapped;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConceptMapGroupElement extends BackboneElement {
    private String code;
    private String display;
    private String valueSet;
    private Boolean noMap;
    private List<ConceptMapGroupElementTarget> target;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConceptMapGroupElementTarget extends BackboneElement {
    private String code;
    private String display;
    private String valueSet;
    private String relationship;
    private String comment;
    private List<ConceptMapGroupElementTargetProperty> property;
    private List<ConceptMapGroupElementTargetDependsOn> dependsOn;
    private List<ConceptMapGroupElementTargetDependsOn> product;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConceptMapGroupElementTargetProperty extends BackboneElement {
    private String code;
    private Coding valueCoding;
    private String valueString;
    private Integer valueInteger;
    private Boolean valueBoolean;
    private OffsetDateTime valueDateTime;
    private BigDecimal valueDecimal;
    private String valueCode;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConceptMapGroupElementTargetDependsOn extends BackboneElement {
    private String attribute;
    private String valueCode;
    private Coding valueCoding;
    private String valueString;
    private Boolean valueBoolean;
    private Quantity valueQuantity;
    private String valueSet;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConceptMapGroupUnmapped extends BackboneElement {
    private String mode;
    private String code;
    private String display;
    private String valueSet;
    private String relationship;
    private String otherMap;
  }
}
