package com.kodality.termx.terminology.fhir.operationdefinition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.core.fhir.BaseFhirResourceHandler;
import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;

@Singleton
public class OperationDefinitionResourceStorage extends BaseFhirResourceHandler {

  private static final String OPERATION_DEFINITION_FILE = "fhir/OperationDefinition.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ResourceLoader resourceLoader;
  private final SearchResult searchResult;

  public OperationDefinitionResourceStorage(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
    this.searchResult = getOperationDefinitions();
  }

  @Override
  public String getResourceType() {
    return "OperationDefinition";
  }

  @Override
  public String getPrivilegeName() {
    return "OperationDefinition";
  }

  @Override
  public ResourceVersion load(String fhirId) {
    return null;
  }

  private ResourceVersion load(String vsId, String versionNumber) {
    return null;
  }

  @Override
  public ResourceVersion save(ResourceId id, ResourceContent content) {
    return null;
  }

  @Override
  public String generateNewId() {
    return null; //TODO:
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    return searchResult;
  }

  private SearchResult getOperationDefinitions() {
    ArrayNode jsonNode = readOperationDefinitions();
    List<ResourceVersion> resourceVersions = new ArrayList<>();
    jsonNode.forEach(node -> {
      resourceVersions.add(
          new ResourceVersion(
              new VersionId("OperationDefinition", node.get("id").asText()),
              new ResourceContent(toJson(node), "json")
          )
      );
    });
    SearchResult searchResult = new SearchResult();
    searchResult.setTotal(resourceVersions.size());
    searchResult.setEntries(resourceVersions);

    return searchResult;

  }

  private ArrayNode readOperationDefinitions() {
    try {
      InputStream is = resourceLoader
          .getResourceAsStream(OPERATION_DEFINITION_FILE)
          .orElseThrow(() -> new IllegalStateException(
              "No operation definition found by path " + OPERATION_DEFINITION_FILE + ". Please check your classpath."
          ));
      String content = IOUtils.toString(is, StandardCharsets.UTF_8);
      return (ArrayNode) MAPPER.readTree(content);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

  private String toJson(JsonNode node) {
    try {
      return MAPPER.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
