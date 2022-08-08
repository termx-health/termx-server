package com.kodality.termserver.integration.orphanet.utils;

import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.integration.icd10est.utils.Icd10Est;
import com.kodality.termserver.integration.icd10est.utils.Icd10Est.Node;
import com.kodality.termserver.integration.icd10est.utils.Icd10EstMapper;
import com.kodality.termserver.integration.orphanet.utils.OrphanetClassificationList.ClassificationNode;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrphanetExtractor {

  public static List<Concept> parseNodes(List<ClassificationNode> nodes, ImportConfiguration configuration, List<EntityProperty> properties) {
    log.info("Mapping nodes to concepts...");
    List<Concept> concepts = new ArrayList<>();
    nodes.forEach(n -> concepts.addAll(parseNodeChild(n, null, configuration, properties)));
    return concepts;
  }

  public static List<Concept> parseNodeChild(ClassificationNode node, ClassificationNode parent, ImportConfiguration configuration, List<EntityProperty> properties) {
    List<Concept> concepts = new ArrayList<>();
    concepts.add(OrphanetMapper.mapConcept(node, parent, configuration, properties));
    if (node.getClassificationNodeChildList() != null && node.getClassificationNodeChildList().getClassificationNodes() != null) {
      node.getClassificationNodeChildList().getClassificationNodes().forEach(child -> concepts.addAll(parseNodeChild(child, node, configuration, properties)));
    }
    return concepts;
  }
}
