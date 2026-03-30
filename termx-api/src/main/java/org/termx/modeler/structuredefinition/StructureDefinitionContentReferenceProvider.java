package org.termx.modeler.structuredefinition;

import java.util.List;

public interface StructureDefinitionContentReferenceProvider {
  List<StructureDefinitionContentReference> findByResourceTypeAndResourceId(String resourceType, String resourceId);
  List<Long> findReferencingStructureDefinitionIds(String resourceType, String resourceId);
}
