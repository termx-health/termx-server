package com.kodality.termx.terminology.terminology.codesystem.entity;

import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyKind;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemSupplementService {
  private final CodeSystemService codeSystemService;
  private final CodeSystemEntityVersionService entityVersionService;
  private final CodeSystemVersionService versionService;
  private final EntityPropertyService entityPropertyService;

  @Transactional
  public List<CodeSystemEntityVersion> supplement(String cs, String csv, List<Long> ids) {
    Map<Long, List<CodeSystemEntityVersion>> versions = entityVersionService.query(new CodeSystemEntityVersionQueryParams()
            .setIds(ids.stream().map(String::valueOf).collect(Collectors.joining(",")))).getData().stream()
        .collect(Collectors.groupingBy(CodeSystemEntityVersion::getCodeSystemEntityId));

    CodeSystem codeSystem = codeSystemService.load(cs).orElseThrow();
    codeSystem.setProperties(Optional.ofNullable(codeSystem.getProperties()).orElse(new ArrayList<>()));
    List<Pair<Long, String>> designations = versions.values().stream().flatMap(Collection::stream).flatMap(v -> v.getDesignations().stream())
        .collect(Collectors.groupingBy(Designation::getDesignationTypeId)).entrySet().stream()
        .map(es -> Pair.of(es.getKey(), es.getValue().get(0).getDesignationType())).toList();
    designations.stream().filter(d -> codeSystem.getProperties().stream().noneMatch(csp -> EntityPropertyKind.designation.equals(csp.getKind()) && csp.getName().equals(d.getValue()))).forEach(d -> {
      EntityProperty ep = entityPropertyService.load(d.getKey()).orElseThrow();
      ep.setId(null);
      entityPropertyService.save(cs, ep);
    });
    List<Pair<Long, String>> properties = versions.values().stream().flatMap(Collection::stream).flatMap(v -> v.getPropertyValues().stream())
        .collect(Collectors.groupingBy(EntityPropertyValue::getEntityPropertyId)).entrySet().stream()
        .map(es -> Pair.of(es.getKey(), es.getValue().get(0).getEntityProperty())).toList();
    properties.stream().filter(p -> codeSystem.getProperties().stream().noneMatch(csp -> EntityPropertyKind.property.equals(csp.getKind()) && csp.getName().equals(p.getValue()))).forEach(p -> {
      EntityProperty ep = entityPropertyService.load(p.getKey()).orElseThrow();
      ep.setId(null);
      entityPropertyService.save(cs, ep);
    });


    versions.values().forEach(val -> val.forEach(version -> {
      version.setBaseEntityVersionId(version.getId());
      version.setId(null);
      version.setCreated(null);
      version.setStatus(PublicationStatus.draft);
      version.setCodeSystem(cs);
      version.setDesignations(null);
      version.setPropertyValues(null);
      version.setAssociations(null);
    }));
    entityVersionService.batchSave(versions, cs);
    List<CodeSystemEntityVersion> result = versions.values().stream().flatMap(Collection::stream).toList();
    if (csv != null) {
      versionService.linkEntityVersions(cs, csv, result.stream().map(CodeSystemEntityVersion::getId).toList());
    }
    return result;
  }
}
