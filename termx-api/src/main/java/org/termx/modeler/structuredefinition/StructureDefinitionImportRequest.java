package org.termx.modeler.structuredefinition;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StructureDefinitionImportRequest {
  /** Import from this URL (fetches the resource). */
  private String url;
  /** Import from this content (JSON or FSH string). */
  private String content;
  /** Format of content: "json" or "fsh". Required when content is set. */
  private String format;
}
