package org.termx.modeler.fhir;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.model.QueryResult;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.termx.core.auth.SessionStore;
import org.termx.core.fhir.BaseFhirMapper;
import org.termx.core.fhir.BaseFhirResourceHandler;
import org.termx.modeler.Privilege;
import org.termx.modeler.structuredefinition.StructureDefinition;
import org.termx.modeler.structuredefinition.StructureDefinitionQueryParams;
import org.termx.modeler.structuredefinition.StructureDefinitionService;
import org.termx.modeler.structuredefinition.StructureDefinitionVersion;

@Singleton
@RequiredArgsConstructor
public class StructureDefinitionResourceStorage extends BaseFhirResourceHandler {
  private final StructureDefinitionService structureDefinitionService;

  @Override
  public String getResourceType() {
    return "StructureDefinition";
  }

  @Override
  public String getPrivilegeName() {
    return "StructureDefinition";
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    StructureDefinitionQueryParams params = fromFhirSearch(criteria);
    QueryResult<StructureDefinition> resp = structureDefinitionService.query(params);
    return new SearchResult(resp.getMeta().getTotal(), resp.getData().stream().map(this::toResourceVersion).toList());
  }

  @Override
  public ResourceVersion load(String fhirId) {
    String[] parts = BaseFhirMapper.parseCompositeId(fhirId);
    String code = parts[0];
    String version = parts[1];
    var params = new StructureDefinitionQueryParams();
    params.setCode(code);
    params.limit(1);
    return structureDefinitionService.query(params).findFirst()
        .flatMap(sd -> {
          if (version != null) {
            return structureDefinitionService.loadVersion(sd.getId(), version)
                .map(ver -> toResourceVersion(sd, ver));
          }
          return structureDefinitionService.load(sd.getId())
              .map(this::toResourceVersion);
        })
        .orElseThrow();
  }

  private ResourceVersion toResourceVersion(StructureDefinition sd) {
    String content = sd.getContent() != null ? sd.getContent() : "{}";
    return new ResourceVersion(
        new VersionId(getResourceType(), sd.getCode() + BaseFhirMapper.SEPARATOR + sd.getVersion()),
        new ResourceContent(content, "json"));
  }

  private ResourceVersion toResourceVersion(StructureDefinition sd, StructureDefinitionVersion ver) {
    String content = ver.getContent() != null ? ver.getContent() : "{}";
    return new ResourceVersion(
        new VersionId(getResourceType(), sd.getCode() + BaseFhirMapper.SEPARATOR + ver.getVersion()),
        new ResourceContent(content, "json"));
  }

  private static StructureDefinitionQueryParams fromFhirSearch(SearchCriterion fhir) {
    return StructureDefinitionSearchMapper.fromFhir(fhir);
  }

  private static class StructureDefinitionSearchMapper extends BaseFhirMapper {
    public static StructureDefinitionQueryParams fromFhir(SearchCriterion fhir) {
      var params = new StructureDefinitionQueryParams();
      getSimpleParams(fhir).forEach((k, v) -> {
        switch (k) {
          case SearchCriterion._COUNT -> params.setLimit(fhir.getCount());
          case SearchCriterion._PAGE -> params.setOffset(getOffset(fhir));
          case "_id" -> params.setCode(v);
          case "url" -> params.setUrl(v);
          case "name" -> params.setName(v);
          case "publisher" -> params.setPublisher(v);
          case "status" -> params.setStatus(v);
          case "version" -> params.setVersion(v);
          default -> throw new ApiClientException("Search by '" + k + "' not supported");
        }
      });
      List<String> ids = SessionStore.require().getPermittedResourceIds(Privilege.SD_VIEW);
      if (ids != null) {
        params.setPermittedIds(ids.stream().map(Long::valueOf).toList());
      }
      return params;
    }
  }
}
