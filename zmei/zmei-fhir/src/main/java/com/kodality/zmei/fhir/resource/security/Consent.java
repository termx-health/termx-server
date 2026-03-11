package com.kodality.zmei.fhir.resource.security;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Expression;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.util.Lists;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Consent extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private String status;
  private List<CodeableConcept> category;
  private Reference subject;
  private LocalDate date;
  private Period period;
  private List<Reference> grantor;
  private List<Reference> grantee;
  private List<Reference> manager;
  private List<Reference> controller;
  private List<Attachment> sourceAttachment;
  private List<Reference> sourceReference;
  private List<CodeableConcept> regulatoryBasis;
  private List<ConsentPolicyBasis> policyBasis;
  private List<Reference> policyText;
  private List<ConsentVerification> verification;
  private String decision;
  private ConsentProvision provision;

  public Consent() {
    super(ResourceType.consent);
  }

  public Consent addIdentifier(Identifier o) {
    this.identifier = Lists.add(this.identifier, o);
    return this;
  }

  public Consent addCategory(CodeableConcept o) {
    this.category = Lists.add(this.category, o);
    return this;
  }

  public Consent addVerification(ConsentVerification o) {
    this.verification = Lists.add(this.verification, o);
    return this;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConsentPolicyBasis extends BackboneElement {
    private Reference reference;
    private String url;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConsentVerification extends BackboneElement {
    private Boolean verified;
    private CodeableConcept verificationType	;
    private Reference verifiedBy;
    private Reference verifiedWith;
    private OffsetDateTime verificationDate;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConsentProvision extends BackboneElement {
    private Period period;
    private List<ConsentProvisionActor> actor;
    private List<CodeableConcept> action;
    private List<Coding> securityLabel;
    private List<Coding> purpose;
    private List<Coding> documentType;
    private List<Coding> resourceType;
    private List<CodeableConcept> code;
    private Period dataPeriod;
    private List<ConsentProvisionData> data;
    private Expression expression;
    private List<ConsentProvision> provision;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConsentProvisionActor extends BackboneElement {
    private CodeableConcept role;
    private Reference reference;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConsentProvisionData extends BackboneElement {
    private String meaning;
    private Reference reference;
  }
}
