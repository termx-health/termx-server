package com.kodality.termserver.terminology.mapset;


import com.kodality.termserver.terminology.association.AssociationTypeService;
import com.kodality.termserver.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.association.AssociationType;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.mapset.MapSet;
import com.kodality.termserver.ts.mapset.MapSetAssociation;
import com.kodality.termserver.ts.mapset.MapSetTransactionRequest;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class MapSetImportService {
  private final MapSetService mapSetService;
  private final AssociationTypeService associationTypeService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  @Transactional
  public void importMapSet(MapSet mapSet, List<AssociationType> associationTypes) {
    associationTypes.forEach(associationTypeService::save);

    MapSetTransactionRequest request = new MapSetTransactionRequest();
    request.setMapSet(mapSet);
    request.setVersion(mapSet.getVersions().get(0));
    request.setAssociations(mapSet.getAssociations());
    importMapSet(request);
  }

  @Transactional
  public void importMapSet(MapSetTransactionRequest request) {
    prepareAssociations(request);
    mapSetService.save(request);
  }

  private void prepareAssociations(MapSetTransactionRequest request) {
    List<MapSetAssociation> associations = request.getAssociations();
    List<String> sourceCodeSystems = request.getMapSet().getSourceCodeSystems();
    List<String> targetCodeSystems = request.getMapSet().getTargetCodeSystems();

    if (CollectionUtils.isEmpty(associations)) {
      return;
    }

    IntStream.range(0, (associations.size() + 1000 - 1) / 1000)
        .mapToObj(i -> associations.subList(i * 1000, Math.min(associations.size(), (i + 1) * 1000))).forEach(batch -> {
          List<CodeSystemEntityVersion> sources = getEntityVersions(batch.stream().map(MapSetAssociation::getSource).toList(), sourceCodeSystems, batch.size());
          List<CodeSystemEntityVersion> targets = getEntityVersions(batch.stream().map(MapSetAssociation::getTarget).toList(), targetCodeSystems, batch.size());
          batch.forEach(a -> a.getSource().setId(sources.stream().filter(s -> s.getCode().equals(a.getSource().getCode())).findFirst().map(CodeSystemEntityVersion::getId).orElse(null)));
          batch.forEach(a -> a.getTarget().setId(targets.stream().filter(t -> t.getCode().equals(a.getTarget().getCode())).findFirst().map(CodeSystemEntityVersion::getId).orElse(null)));
        });
  }

  private List<CodeSystemEntityVersion> getEntityVersions(List<CodeSystemEntityVersion> versions, List<String> codeSystems, int limit) {
    if (CollectionUtils.isEmpty(codeSystems)) {
      return new ArrayList<>();
    }
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams()
        .setCode(versions.stream().map(CodeSystemEntityVersion::getCode).filter(StringUtils::isNotEmpty).collect(Collectors.joining(",")))
        .setCodeSystem(String.join(",", codeSystems));
    params.setLimit(limit);
    return codeSystemEntityVersionService.query(params).getData();
  }
}
