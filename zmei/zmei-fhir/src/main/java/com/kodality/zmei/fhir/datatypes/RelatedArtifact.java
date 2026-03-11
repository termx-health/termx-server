package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class RelatedArtifact extends Element {
  private String type;
  private List<CodeableConcept> classifier;
  private String label;
  private String display;
  private String citation;
  private Attachment document;
  private String resource;
  private Reference resourceReference;
  private String publicationStatus;
  private LocalDate publicationDate;
}
