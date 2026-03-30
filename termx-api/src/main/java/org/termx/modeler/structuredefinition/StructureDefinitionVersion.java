package org.termx.modeler.structuredefinition;

import com.kodality.commons.model.LocalizedName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

@Getter
@Setter
@Accessors(chain = true)
public class StructureDefinitionVersion {
  private Long id;
  private Long structureDefinitionId;
  private String version;
  private String fhirId;
  private String content;
  private String contentType;
  private String contentFormat;
  private String status;
  private OffsetDateTime releaseDate;
  private LocalizedName description;
}
