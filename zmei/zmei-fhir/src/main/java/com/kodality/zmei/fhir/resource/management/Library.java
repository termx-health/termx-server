package com.kodality.zmei.fhir.resource.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.ContactDetail;
import com.kodality.zmei.fhir.datatypes.DataRequirement;
import com.kodality.zmei.fhir.datatypes.Duration;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.ParameterDefinition;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.RelatedArtifact;
import com.kodality.zmei.fhir.datatypes.UsageContext;
import com.kodality.zmei.fhir.datatypes.VirtualServiceDetail;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.resource.management.Encounter.EncounterLocation;
import java.time.OffsetDateTime;
import java.util.List;
import javax.naming.Binding;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Library extends DomainResource implements Identifiable {
  private String url;
  private List<Identifier> identifier;
  private String version;
  private String versionAlgorithmString;
  private Coding versionAlgorithmCoding;
  private String name;
  private String title;
  private String subtitle;
  private String status;
  private Boolean experimental;
  private CodeableConcept type;
  private CodeableConcept subjectCodeableConcept;
  private Reference subjectReference;
  private OffsetDateTime date;
  private String publisher;
  private List<ContactDetail> contact;
  private String description;
  private List<UsageContext> useContext;
  private String purpose;
  private String usage;
  private String copyright;
  private String copyrightLabel;
  private OffsetDateTime approvalDate;
  private OffsetDateTime lastReviewDate;
  private Period effectivePeriod;
  private List<ContactDetail> author;
  private List<ContactDetail> editor;
  private List<ContactDetail> reviewer;
  private List<ContactDetail> endorser;
  private List<RelatedArtifact> relatedArtifact;
  private List<ParameterDefinition> parameter;
  private List<DataRequirement> dataRequirement;
  private List<Attachment> content;

  public Library() {
    super(ResourceType.library);
  }

}
