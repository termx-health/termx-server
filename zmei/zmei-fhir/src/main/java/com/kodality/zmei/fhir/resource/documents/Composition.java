package com.kodality.zmei.fhir.resource.documents;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.Identifiable;
import com.kodality.zmei.fhir.datatypes.Annotation;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.CodeableReference;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Narrative;
import com.kodality.zmei.fhir.datatypes.Period;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.RelatedArtifact;
import com.kodality.zmei.fhir.datatypes.UsageContext;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.util.Lists;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Composition extends DomainResource implements Identifiable {
  private String url;
  private List<Identifier> identifier;
  private String version;
  private String status;
  private CodeableConcept type;
  private List<CodeableConcept> category;
  private List<Reference> subject;
  private Reference encounter;
  private OffsetDateTime date;
  private UsageContext useContext;
  private List<Reference> author;
  private String name;
  private String title;
  private Annotation note;
  private List<CompositionAttester> attester;
  private Reference custodian;
  private List<RelatedArtifact> relatesTo;
  private List<CompositionEvent> event;
  private List<CompositionSection> section;

  public Composition() {
    super(ResourceType.composition);
  }

  public Composition addSection(CompositionSection section) {
    this.section = Lists.add(this.section, section);
    return this;
  }

  public Composition addEvent(CompositionEvent event) {
    this.event = Lists.add(this.event, event);
    return this;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CompositionAttester extends BackboneElement {
    private CodeableConcept mode;
    private OffsetDateTime time;
    private Reference party;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CompositionEvent extends BackboneElement {
    private Period period;
    private List<CodeableReference> detail;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CompositionSection extends BackboneElement {
    private String title;
    private CodeableConcept code;
    private List<Reference> author;
    private Reference focus;
    private Narrative text;
    private CodeableConcept orderedBy;
    private List<Reference> entry;
    private CodeableConcept emptyReason;
    private List<CompositionSection> section;

    public CompositionSection addSection(CompositionSection section) {
      this.section = Lists.add(this.section, section);
      return this;
    }

    public CompositionSection addEntry(Reference entry) {
      this.entry = Lists.add(this.entry, entry);
      return this;
    }
  }
}
