package com.kodality.termserver.integration.icd10est.utils;

import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.common.ImportConfiguration;
import com.kodality.termserver.integration.icd10est.utils.Icd10Est.Node;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Extractor {

  public static List<Concept> parseDiagnoses(List<Icd10Est> diagnoses, ImportConfiguration configuration, List<EntityProperty> properties) {
    log.info("Mapping diagnoses to concepts...");
    List<Concept> concepts = new ArrayList<>();
    diagnoses.forEach(d -> concepts.addAll(parseNodeChild(d.getChapter(), null, configuration, properties)));
    return concepts;
  }

  public static List<Concept> parseNodeChild(Node element, Node parent, ImportConfiguration configuration, List<EntityProperty> properties) {
    List<Concept> concepts = new ArrayList<>();
    concepts.add(Icd10EstMapper.mapConcept(element, parent, configuration, properties));
    if (element.getChildren() != null) {
      element.getChildren().forEach(child -> concepts.addAll(parseNodeChild(child, element, configuration, properties)));
    }
    if (element.getSub() != null) {
      element.getSub().forEach(sub -> parseNodeChild(sub, element, configuration, properties));
    }
    return concepts;
  }
}
