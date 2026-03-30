package org.termx.modeler.structuredefinition;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class StructureDefinitionContentReference {
  private Long id;
  private Long structureDefinitionVersionId;
  private String url;
  private String resourceType;
  private String resourceId;
}
