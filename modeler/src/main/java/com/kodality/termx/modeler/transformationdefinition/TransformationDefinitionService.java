package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.StructureMap;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Singleton
public class TransformationDefinitionService {
  private final TransformationDefinitionRepository repository;
  private final TransformerService transformerService;

  public TransformationDefinition load(Long id) {
    TransformationDefinition td = repository.load(id);
    saveFhirResource(td);
    return td;
  }

  public QueryResult<TransformationDefinition> search(TransformationDefinitionQueryParams params) {
    QueryResult<TransformationDefinition> resp = repository.search(params);
    if (!params.isSummary()) {
      resp.getData().forEach(this::saveFhirResource);
    }
    return resp;
  }

  public void save(TransformationDefinition def) {
    def.setFhirResource(toFhirStructureMap(def));
    repository.save(def);
  }

  @Transactional
  public TransformationDefinition duplicate(Long id) {
    TransformationDefinition def = load(id);
    def.setId(null);
    def.setName(def.getName() + "_duplicate");
    save(def);
    return def;
  }

  public void delete(Long id) {
    repository.delete(id);
  }


  // as the side effect of query/save, the FHIR structure map is created and saved based on the transformation definition's mapping

  private void saveFhirResource(TransformationDefinition td) {
    if (td.getFhirResource() != null) {
      return;
    }
    try {
      repository.updateFhirResource(td.getId(), toFhirStructureMap(td));
    } catch (Exception ignored) {
    }
  }

  public Map<?, ?> toFhirStructureMap(TransformationDefinition def) {
    StructureMap fhir = transformerService.getStructureMap(def.getMapping());
    if (CollectionUtils.isNotEmpty(fhir.getExtension())) {
      fhir.getExtension().removeIf(ext -> ext.getUrl().equals("fml-export"));
      fhir.getExtension().removeIf(ext -> ext.getUrl().equals("fml-svg"));
    }
    if (StringUtils.isBlank(fhir.getId())) {
      fhir.setId(def.getName());
    }
    if (StringUtils.isBlank(fhir.getUrl())) {
      throw new RuntimeException("StructureMap's URL is missing");
    }
    try {
      String json = new JsonParser().setOutputStyle(OutputStyle.PRETTY).composeString(fhir);
      return JsonUtil.toMap(json);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
