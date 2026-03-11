package com.kodality.zmei.fhir.resource.summary;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Age;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Range;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.Timing;
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
public class Procedure extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private List<String> instantiatesCanonical;
  private List<String> instantiatesUri;
  private List<Reference> basedOn;
  private List<Reference> partOf;
  private String status;
  private CodeableConcept statusReason;
  private List<CodeableConcept> category;
  private CodeableConcept code;
  private Reference subject;
  private Reference focus;
  private Reference encounter;
  private OffsetDateTime occurrenceDateTime;
  private Period occurrencePeriod;
  private String occurrenceString;
  private Age occurrenceAge;
  private Range occurrenceRange;
  private Timing occurrenceTiming;
  private OffsetDateTime recorded;
  private Reference recorder;
  private Boolean reportedBoolean;
  private Reference reportedReference;
  private List<ProcedurePerformer> performer;
  private Reference location;
  private List<CodeableReference> reason;
  private List<CodeableConcept> bodySite;
  private CodeableConcept outcome;
  private List<Reference> report;
  private List<CodeableReference> complication;
  private List<CodeableConcept> followUp;
  private List<Annotation> note;
  private List<ProcedureFocalDevice> focalDevice;
  private List<CodeableReference> used;

  public Procedure() {
    super(ResourceType.procedure);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ProcedurePerformer extends BackboneElement {
    private CodeableConcept function;
    private Reference actor;
    private Reference onBehalfOf;
    private Period period;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ProcedureFocalDevice extends BackboneElement {
    private CodeableConcept action;
    private Reference manipulated;
  }
}
