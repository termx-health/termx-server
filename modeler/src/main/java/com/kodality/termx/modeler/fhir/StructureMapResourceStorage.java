package com.kodality.termx.modeler.fhir;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.fhir.BaseFhirMapper;
import com.kodality.termx.core.fhir.BaseFhirResourceHandler;
import com.kodality.termx.modeler.Privilege;
import com.kodality.termx.modeler.structuredefinition.StructureDefinition;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionQueryParams;
import com.kodality.termx.modeler.structuredefinition.StructureDefinitionService;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceType;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResourceReference;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionQueryParams;
import com.kodality.termx.modeler.transformationdefinition.TransformationDefinitionService;
import com.kodality.termx.modeler.transformationdefinition.TransformerService;
import com.kodality.termx.terminology.terminology.mapset.MapSetService;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;
import org.hl7.fhir.r5.model.StructureMap;
import org.hl7.fhir.r5.model.StructureMap.StructureMapGroupRuleComponent;
import org.springframework.transaction.annotation.Transactional;

import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceSource.local;
import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceSource.statik;
import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceType.conceptMap;
import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceType.definition;
import static com.kodality.termx.modeler.transformationdefinition.TransformationDefinition.TransformationDefinitionResource.TransformationDefinitionResourceType.mapping;
import static org.hl7.fhir.r5.model.StructureMap.StructureMapTransform.TRANSLATE;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class StructureMapResourceStorage extends BaseFhirResourceHandler {
  private final StructureDefinitionService structureDefinitionService;
  private final TransformationDefinitionService transformationDefinitionService;
  private final TransformerService transformerService;
  private final MapSetService mapSetService;

  @Override
  public String getResourceType() {
    return "StructureMap";
  }

  @Override
  public String getPrivilegeName() {
    return "TransformationDefinition";
  }


  @Override
  public SearchResult search(SearchCriterion criteria) {
    TransformationDefinitionQueryParams params = StructureMapFhirMapper.fromFhir(criteria);
    QueryResult<TransformationDefinition> resp = transformationDefinitionService.search(params);
    return new SearchResult(resp.getMeta().getTotal(), resp.getData().stream().map(this::toResourceVersion).toList());
  }

  @Override
  public ResourceVersion load(String fhirId) {
    var q = new TransformationDefinitionQueryParams();
    q.setFhirExists(true);
    q.setFhirIds(fhirId);
    q.limit(1);
    return transformationDefinitionService.search(q).findFirst().map(this::toResourceVersion).orElseThrow();
  }

  @Override
  @Transactional
  public ResourceVersion save(ResourceId id, ResourceContent content) {
    StructureMap sm = transformerService.parse(content.getValue());

    // Definitions
    // fixme: save will fail, if any of resources is not saved in the TermX itself
    // todo: traverse rules, they may contain resources that should be imported
    List<TransformationDefinitionResource> definitions = sm.getStructure().stream().map(c -> {
      QueryResult<StructureDefinition> resp = structureDefinitionService.query(new StructureDefinitionQueryParams().setUrls(c.getUrl()).limit(2));
      if (resp.getData().size() != 1) {
        throw new FhirException(400, IssueType.NOTFOUND, "StructureDefinition \"" + c.getUrl() + "\" is missing on the server!");
      }

      var res = new TransformationDefinitionResource();
      res.setName(c.getAlias());
      res.setType(definition);
      res.setSource(local);
      res.setReference(new TransformationDefinitionResourceReference());
      res.getReference().setLocalId(resp.getData().get(0).getId().toString());
      return res;
    }).toList();

    // Concept Maps
    var conceptMapUris = new HashSet<String>();
    sm.getGroup().forEach(g -> {
      traverserRules(g.getRule(), r -> r.getTarget().stream()
          .filter(t -> TRANSLATE.equals(t.getTransform()))
          .filter(t -> t.getParameter().size() == 3)
          .forEach(t -> conceptMapUris.add(t.getParameter().get(1).getValueStringType().getValue())));
    });

    List<TransformationDefinitionResource> conceptMaps = conceptMapUris.stream().map(uri -> {
      QueryResult<MapSet> resp = mapSetService.query(new MapSetQueryParams().setUri(uri).limit(2));
      if (resp.getData().size() != 1) {
        throw new FhirException(400, IssueType.NOTFOUND, "ConceptMap \"" + uri + "\" is missing on the server!");
      }

      var res = new TransformationDefinitionResource();
      res.setName(resp.getData().get(0).getUri());
      res.setType(conceptMap);
      res.setSource(local);
      res.setReference(new TransformationDefinitionResourceReference());
      res.getReference().setLocalId(resp.getData().get(0).getId());
      return res;
    }).toList();

    // Imports
    List<TransformationDefinitionResource> importMaps = sm.getImport().stream().map(i -> {
      QueryResult<TransformationDefinition> resp =
          transformationDefinitionService.search(new TransformationDefinitionQueryParams().setFhirUrls(i.getValue()).limit(2));
      if (resp.getData().size() != 1) {
        throw new FhirException(400, IssueType.NOTFOUND, "StructureMap \"" + i.getValue() + "\" is missing on the server!");
      }

      var res = new TransformationDefinitionResource();
      res.setName(resp.getData().get(0).getName());
      res.setType(mapping);
      res.setSource(local);
      res.setReference(new TransformationDefinitionResourceReference());
      res.getReference().setLocalId(resp.getData().get(0).getId().toString());
      return res;
    }).toList();


    // TransformationDefinition
    var td = new TransformationDefinition();
    td.setName(sm.getName());
    td.setResources(Stream.of(definitions, importMaps, conceptMaps).flatMap(Collection::stream).toList());

    var mapping = new TransformationDefinitionResource();
    mapping.setSource(!sm.getGroup().isEmpty() ? sm.getGroup().get(0).getName() : sm.getName());
    mapping.setType(TransformationDefinitionResourceType.mapping);
    mapping.setSource(statik);
    mapping.setReference(new TransformationDefinitionResourceReference());
    mapping.getReference().setContent(toJson(sm));
    td.setMapping(mapping);

    if (id.getResourceId() != null) {
      QueryResult<TransformationDefinition> resp =
          transformationDefinitionService.search(new TransformationDefinitionQueryParams().setFhirIds(id.getResourceId()).limit(2));
      if (resp.getData().size() != 1) {
        throw new FhirException(400, IssueType.INVALID, "Matched multiple StructureMap resource by ID \"" + id.getResourceId() + "\"");
      } else {
        td.setId(resp.getData().get(0).getId());
      }
    }

    transformationDefinitionService.save(td);
    return toResourceVersion(td);
  }

  @Override
  public String generateNewId() {
    return null; //TODO:
  }


  private String toJson(StructureMap sm) {
    try {
      return new JsonParser().setOutputStyle(OutputStyle.PRETTY).composeString(sm);
    } catch (IOException e) {
      throw new FhirException(400, IssueType.INVALID, "Invalid StructureMap definition");
    }
  }

  private ResourceVersion toResourceVersion(TransformationDefinition td) {
    return new ResourceVersion(
        new VersionId(getResourceType(), td.getName()),
        new ResourceContent(JsonUtil.toJson(td.getFhirResource()), "json"));
  }

  private void traverserRules(List<StructureMapGroupRuleComponent> rules, Consumer<StructureMapGroupRuleComponent> action) {
    if (rules != null) {
      rules.forEach(r -> {
        action.accept(r);
        traverserRules(r.getRule(), action);
      });
    }
  }


  private static class StructureMapFhirMapper extends BaseFhirMapper {
    public static TransformationDefinitionQueryParams fromFhir(SearchCriterion fhir) {
      var params = new TransformationDefinitionQueryParams();
      params.setFhirExists(true);
      getSimpleParams(fhir).forEach((k, v) -> {
        switch (k) {
          case SearchCriterion._COUNT -> params.setLimit(fhir.getCount());
          case SearchCriterion._PAGE -> params.setOffset(getOffset(fhir));
          case "_id" -> params.setFhirIds(v);
          case "url" -> params.setFhirUrls(v);
          case "description" -> params.setFhirDescriptionContains(v);
          case "title" -> params.setFhirTitleContains(v);
          case "status" -> params.setFhirStatuses(v);
          default -> throw new ApiClientException("Search by '" + k + "' not supported");
        }
      });

      List<String> ids = SessionStore.require().getPermittedResourceIds(Privilege.TD_VIEW);
      if (ids != null) {
        params.setPermittedIds(ids.stream().map(Long::valueOf).toList());
      }
      return params;
    }
  }
}
