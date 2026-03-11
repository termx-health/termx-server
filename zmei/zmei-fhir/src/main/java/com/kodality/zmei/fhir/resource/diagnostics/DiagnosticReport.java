package com.kodality.zmei.fhir.resource.diagnostics;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.Attachment;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
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
public class DiagnosticReport extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private List<Reference> basedOn;
  private String status;
  private List<CodeableConcept> category;
  private CodeableConcept code;
  private Reference subject;
  private Reference encounter;
  private OffsetDateTime effectiveDateTime;
  private Period effectivePeriod;
  private OffsetDateTime issued;
  private List<Reference> performer;
  private List<Reference> resultsInterpreter;
  private List<Reference> specimen;
  private List<Reference> result;
  private List<Annotation> note;
  private List<Reference> study;
  private List<DiagnosticReportSupportingInfo> supportingInfo;
  private List<DiagnosticReportMedia> media;
  private Reference composition;
  private String conclusion;
  private List<CodeableConcept> conclusionCode;
  private List<Attachment> presentedForm;

  public DiagnosticReport() {
    super(ResourceType.diagnosticReport);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DiagnosticReportSupportingInfo extends BackboneElement {
    private CodeableConcept type;
    private Reference reference;
  }
  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DiagnosticReportMedia extends BackboneElement {
    private String comment;
    private Reference link;
  }
}
