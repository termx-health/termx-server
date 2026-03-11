package com.kodality.zmei.fhir.resource.security;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Expression;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.Signature;
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
public class Provenance extends DomainResource {
  private List<Reference> target;
  private Period occurredPeriod;
  private OffsetDateTime occurredDateTime;
  private OffsetDateTime recorded;
  private List<String> policy;
  private Reference location;
  private List<CodeableReference> authorization;
  private CodeableConcept activity;
  private List<Reference> basedOn;
  private Reference patient;
  private Reference encounter;
  private List<ProvenanceAgent> agent;
  private List<ProvenanceEntity> entity;
  private List<Signature> signature;


  public Provenance() {
    super(ResourceType.provenance);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ProvenanceAgent extends BackboneElement {
    private CodeableConcept type;
    private List<CodeableConcept> role;
    private Reference who;
    private Reference onBehalfOf;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ProvenanceEntity extends BackboneElement {
    private String role;
    private Reference what;
    private List<ProvenanceAgent> agent;
  }

}
