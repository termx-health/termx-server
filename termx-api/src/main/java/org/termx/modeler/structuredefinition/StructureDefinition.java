package org.termx.modeler.structuredefinition;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class StructureDefinition {
  private Long id;
  private String url;
  private String code;
  private String name;
  private String parent;
  private String publisher;
  /** Populated from current or requested version when loading. */
  private String content;
  private String contentType;
  private String contentFormat;
  private String version;
  private String fhirId;
  private String status;
  private java.time.OffsetDateTime releaseDate;
}
