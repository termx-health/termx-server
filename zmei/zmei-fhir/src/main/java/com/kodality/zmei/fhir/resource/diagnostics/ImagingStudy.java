package com.kodality.zmei.fhir.resource.diagnostics;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.Identifier;
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
public class ImagingStudy extends DomainResource implements Identifiable {
  private List<Identifier> identifier;
  private String status;
  private List<CodeableConcept> modality;
  private Reference subject;
  private Reference encounter;
  private OffsetDateTime started;
  private List<Reference> basedOn;
  private List<Reference> partOf;
  private Reference referrer;
  private List<Reference> endpoint;
  private Integer numberOfSeries;
  private Integer numberOfInstances;
  private CodeableReference procedure;
  private Reference location;
  private List<CodeableReference> reason;
  private List<Annotation> note;
  private String description;
  private List<ImagingStudySeries> series;

  public ImagingStudy() {
    super(ResourceType.imagingStudy);
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ImagingStudySeries extends BackboneElement {
    private String uid;
    private Integer number;
    private CodeableConcept modality;
    private String description;
    private Integer numberOfInstances;
    private List<Reference> endpoint;
    private CodeableReference bodySite;
    private CodeableConcept laterality;
    private List<Reference> specimen;
    private OffsetDateTime started;
    private List<ImagingStudySeriesPerformer> performer;
    private List<ImagingStudySeriesInstance> instance;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ImagingStudySeriesPerformer extends BackboneElement {
    private CodeableConcept function;
    private Reference actor;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ImagingStudySeriesInstance extends BackboneElement {
    private String uid;
    private Coding sopClass;
    private Integer number;
    private String title;
  }

}
