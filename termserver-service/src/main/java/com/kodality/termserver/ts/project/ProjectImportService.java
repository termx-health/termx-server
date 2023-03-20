package com.kodality.termserver.ts.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termserver.fhir.conceptmap.ConceptMapFhirImportService;
import com.kodality.termserver.fhir.valueset.ValueSetFhirImportService;
import com.kodality.termserver.ts.mapset.MapSet;
import com.kodality.termserver.ts.mapset.MapSetQueryParams;
import com.kodality.termserver.ts.project.overview.ProjectOverview;
import com.kodality.termserver.ts.project.projectpackage.Package;
import com.kodality.termserver.ts.project.projectpackage.PackageResourceType;
import com.kodality.termserver.ts.project.projectpackage.PackageService;
import com.kodality.termserver.ts.project.projectpackage.PackageStatus;
import com.kodality.termserver.ts.project.projectpackage.PackageVersion;
import com.kodality.termserver.ts.project.projectpackage.PackageVersion.PackageResource;
import com.kodality.termserver.ts.project.projectpackage.version.PackageVersionService;
import com.kodality.termserver.ts.project.server.TerminologyServer;
import com.kodality.termserver.ts.project.server.TerminologyServerQueryParams;
import com.kodality.termserver.ts.project.server.TerminologyServerService;
import com.kodality.termserver.ts.codesystem.CodeSystemService;
import com.kodality.termserver.ts.mapset.MapSetService;
import com.kodality.termserver.ts.valueset.ValueSetService;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetQueryParams;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.reactivex.Flowable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.reactivestreams.Publisher;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ProjectImportService {
  private final ProjectService projectService;
  private final PackageService packageService;
  private final PackageVersionService packageVersionService;
  private final TerminologyServerService terminologyServerService;

  private final CodeSystemService codeSystemService;
  private final CodeSystemFhirImportService csFhirImportService;

  private final ValueSetService valueSetService;
  private final ValueSetFhirImportService vsFhirImportService;

  private final MapSetService mapSetService;
  private final ConceptMapFhirImportService cmFhirImportService;

  @Transactional
  public void importProject(Publisher<CompletedFileUpload> file) {
    if (file == null) {
      return;
    }
    String yaml = new String(readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet()));
    ProjectOverview overview = toJson(yaml);

    if (overview == null || overview.getCode() == null) {
      throw ApiError.TE903.toApiException();
    }
    if (overview.getPack() == null || overview.getPack().getCode() == null || overview.getPack().getVersion() == null) {
      throw ApiError.TE904.toApiException();
    }

    List<TerminologyServer> terminologyServers = CollectionUtils.isEmpty(overview.getSourceOfTruth()) ? List.of() : loadServers(overview.getSourceOfTruth());
    List<PackageResource> resources = CollectionUtils.isEmpty(overview.getSourceOfTruth()) ? List.of() : toResources(overview.getSourceOfTruth());
    resources = importExternalResources(resources, terminologyServers);
    resources.addAll(findLocalResources(overview, resources));

    Project project = prepareProject(overview.getCode(), terminologyServers);
    Package pack = preparePackage(project.getId(), overview.getPack().getCode());
    saveVersion(pack.getId(), overview.getPack().getVersion(), resources);
  }

  private Project prepareProject(String code, List<TerminologyServer> terminologyServers) {
    Project project = projectService.load(code);
    if (project == null) {
      project = new Project();
      project.setActive(true);
      project.setCode(code);
      project.setNames(new LocalizedName(Map.of(Language.en, code)));
    }
    project.setTerminologyServers(terminologyServers.stream().map(TerminologyServer::getCode).toList());
    projectService.save(project);
    return project;
  }

  private Package preparePackage(Long projectId, String code) {
    Package pack = packageService.load(projectId, code);
    if (pack == null) {
      pack = new Package();
      pack.setCode(code);
      pack.setStatus(PackageStatus.active);
      packageService.save(pack, projectId);
    }
    return pack;
  }

  private void saveVersion(Long packageId, String ver, List<PackageResource> resources) {
    PackageVersion version = packageVersionService.load(packageId, ver);
    if (version == null) {
      version = new PackageVersion();
      version.setVersion(ver);
    }
    version.setResources(resources);
    packageVersionService.save(packageId, version);
  }


  private List<PackageResource> importExternalResources(List<PackageResource> resources, List<TerminologyServer> servers) {
    Map<String, List<PackageResource>> groupedByType = resources.stream().collect(Collectors.groupingBy(PackageResource::getResourceType));

    Map<String, Integer> importPriority = Map.of(PackageResourceType.code_system, 1, PackageResourceType.value_set, 2, PackageResourceType.map_set, 3);
    List<String> succeededIds = new ArrayList<>();
    groupedByType.keySet().stream().sorted(Comparator.comparing(importPriority::get)).forEach(type -> {
      List<String> ids = groupedByType.get(type).stream().map(PackageResource::getResourceId).toList();
      if (type.equals(PackageResourceType.code_system)) {
        List<String> existingIds = queryCodeSystems(ids);
        List<Pair<String, String>> urls = getUrls(groupedByType.get(type), existingIds, servers, "CodeSystem");
        List<String> importedIds = importCodeSystems(urls);
        succeededIds.addAll(existingIds);
        succeededIds.addAll(importedIds);
      }
      if (type.equals(PackageResourceType.value_set)) {
        List<String> existingIds = queryValueSets(ids);
        List<Pair<String, String>> urls = getUrls(groupedByType.get(type), existingIds, servers, "ValueSet");
        List<String> importedIds = importValueSets(urls);
        succeededIds.addAll(existingIds);
        succeededIds.addAll(importedIds);
      }
      if (type.equals(PackageResourceType.map_set)) {
        List<String> existingIds = queryMapSets(ids);
        List<Pair<String, String>> urls = getUrls(groupedByType.get(type), existingIds, servers, "ConceptMap");
        List<String> importedIds = importMapSets(urls);
        succeededIds.addAll(existingIds);
        succeededIds.addAll(importedIds);
      }
    });
    return resources.stream().filter(resource -> succeededIds.stream().anyMatch(id -> id.equals(resource.getResourceId()))).collect(Collectors.toList());
  }

  private List<Pair<String, String>> getUrls(List<PackageResource> resources, List<String> existingIds, List<TerminologyServer> servers, String type) {
    return resources.stream()
        .filter(r -> existingIds.stream().noneMatch(id -> id.equals(r.getResourceId())))
        .map(r -> Pair.of(servers.stream().filter(s -> s.getCode().equals(r.getTerminologyServer())).findFirst().orElse(null), r))
        .filter(r -> r.getKey() != null)
        .map(r -> Pair.of(r.getValue().getResourceId(), r.getKey().getRootUrl() + "/fhir/" + type + "/" + r.getValue().getResourceId())).toList();
  }

  private List<PackageResource> findLocalResources(ProjectOverview overview, List<PackageResource> externalResources) {
    List<PackageResource> resources = new ArrayList<>();
    resources.addAll(queryCodeSystems(overview.getCodeSystem()).stream().map(id -> new PackageResource().setResourceId(id).setResourceType(PackageResourceType.code_system)).toList());
    resources.addAll(queryValueSets(overview.getCodeSystem()).stream().map(id -> new PackageResource().setResourceId(id).setResourceType(PackageResourceType.value_set)).toList());
    resources.addAll(queryMapSets(overview.getCodeSystem()).stream().map(id -> new PackageResource().setResourceId(id).setResourceType(PackageResourceType.map_set)).toList());
    return resources.stream().filter(r -> externalResources.stream().noneMatch(er -> er.getResourceType().equals(r.getResourceType()) && er.getResourceId().equals(r.getResourceId()))).toList();
  }

  private List<TerminologyServer> loadServers(Map<String, Map<String, List<String>>> sourceOfTruth) {
    List<String> serverCodes = sourceOfTruth.keySet().stream().toList();
    TerminologyServerQueryParams params = new TerminologyServerQueryParams().setCodes(String.join(",", serverCodes));
    params.setLimit(serverCodes.size());
    return terminologyServerService.query(params).getData();
  }

  private List<PackageResource> toResources(Map<String, Map<String, List<String>>> sourceOfTruth) {
    return sourceOfTruth.keySet().stream()
        .flatMap(server -> sourceOfTruth.get(server).keySet().stream()
            .flatMap(type -> sourceOfTruth.get(server).get(type).stream()
                .map(id -> {
                  PackageResource resource = new PackageResource();
                  resource.setTerminologyServer(server);
                  resource.setResourceType(type);
                  resource.setResourceId(id);
                  return resource;
                }))).toList();
  }

  private ProjectOverview toJson(String yaml) {
    try {
      ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
      return yamlReader.readValue(yaml, ProjectOverview.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  // CodeSystem
  private List<String> queryCodeSystems(List<String> ids) {
    CodeSystemQueryParams params = new CodeSystemQueryParams().setIds(String.join(",", ids));
    params.setLimit(ids.size());
    return codeSystemService.query(params).getData().stream().map(CodeSystem::getId).toList();
  }

  private List<String> importCodeSystems(List<Pair<String, String>> urls) {
    return urls.stream().map(url -> {
      try {
        csFhirImportService.importCodeSystemFromUrl(url.getValue());
        return url.getKey();
      } catch (Exception e) {
        log.error(e.getMessage());
        return null;
      }
    }).filter(Objects::nonNull).toList();
  }

  // ValueSet
  private List<String> queryValueSets(List<String> ids) {
    ValueSetQueryParams params = new ValueSetQueryParams().setIds(String.join(",", ids));
    params.setLimit(ids.size());
    return valueSetService.query(params).getData().stream().map(ValueSet::getId).toList();
  }

  private List<String> importValueSets(List<Pair<String, String>> urls) {
    return urls.stream().map(url -> {
      try {
        vsFhirImportService.importValueSet(url.getValue());
        return url.getKey();
      } catch (Exception e) {
        log.error(e.getMessage());
        return null;
      }
    }).filter(Objects::nonNull).toList();
  }

  // MapSet
  private List<String> queryMapSets(List<String> ids) {
    MapSetQueryParams params = new MapSetQueryParams().setIds(String.join(",", ids));
    params.setLimit(ids.size());
    return mapSetService.query(params).getData().stream().map(MapSet::getId).toList();
  }

  private List<String> importMapSets(List<Pair<String, String>> urls) {
    return urls.stream().map(url -> {
      try {
        cmFhirImportService.importMapSet(url.getValue());
        return url.getKey();
      } catch (Exception e) {
        log.error(e.getMessage());
        return null;
      }
    }).filter(Objects::nonNull).toList();
  }

  private byte[] readBytes(CompletedFileUpload file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
