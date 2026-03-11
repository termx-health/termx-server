package com.kodality.zmei.fhir.resource.documents;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
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
public class DocumentReference extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private String version;
  private List<Reference> basedOn;
  private String status;
  private String docStatus;
  private List<CodeableConcept> modality;
  private CodeableConcept type;
  private List<CodeableConcept> category;
  private Reference subject;
  private List<Reference> context;
  private List<CodeableReference> event;
  private List<CodeableReference> bodySite;
  private CodeableConcept faciiltyType;
  private CodeableConcept practiceSetting;
  private Period period;
  private OffsetDateTime date;
  private List<Reference> author;
  private List<DocumentReferenceAttester> attester;
  private Reference custodian;
  private List<DocumentReferenceRelatesTo> relatesTo;
  private String description;
  private List<CodeableConcept> securityLabel;
  private List<DocumentReferenceContent> content;

  public DocumentReference() {
    super(ResourceType.documentReference);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DocumentReferenceAttester extends BackboneElement {
    private CodeableConcept mode;
    private OffsetDateTime time;
    private Reference party;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DocumentReferenceRelatesTo extends BackboneElement {
    private String code;
    private Reference target;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DocumentReferenceContent extends BackboneElement {
    private Attachment attachment;
    private List<String> profile;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DocumentReferenceContentProfile extends BackboneElement {
    private Coding valueCoding;
    private String valueUri;
    private String valueCanonical;
  }
}
