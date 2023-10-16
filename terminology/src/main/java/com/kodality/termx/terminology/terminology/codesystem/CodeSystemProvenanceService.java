package com.kodality.termx.terminology.terminology.codesystem;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.Provenance.ProvenanceChange;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.core.sys.provenance.ProvenanceUtil;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionRepository;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemTransactionRequest;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.ConceptTransactionRequest;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import jakarta.inject.Singleton;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;

@Singleton
@RequiredArgsConstructor
public class CodeSystemProvenanceService {
  private final ProvenanceService provenanceService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final CodeSystemEntityVersionRepository codeSystemEntityVersionRepository;

  public List<Provenance> find(String codeSystem, String versionCode) {
    if (versionCode == null) {
      return provenanceService.find("CodeSystem|" + codeSystem);
    }
    return codeSystemVersionService.load(codeSystem, versionCode).map(csv ->
        provenanceService.find("CodeSystemVersion|" + csv.getId().toString())
    ).orElse(List.of());
  }

  public void create(Provenance p) {
    provenanceService.create(p);
  }

  public void provenanceCodeSystemTransactionRequest(String action, CodeSystemTransactionRequest request, Runnable save) {
    provenanceCodeSystem(action, request.getCodeSystem().getId(), () -> {
      if (request.getVersion() != null) {
        provenanceCodeSystemVersion(action, request.getCodeSystem().getId(), request.getVersion().getVersion(), () -> {
          save.run();
        });
        return;
      }
      save.run();
    });
  }

  public void provenanceCodeSystem(String action, String csId, Runnable save) {
    CodeSystem before = codeSystemService.load(csId).orElse(null);
    save.run();
    CodeSystem after = codeSystemService.load(csId).orElseThrow();
    provenanceService.create(new Provenance(action, "CodeSystem", csId)
        .setChanges(before == null ? null : diff(before, after))
        .created(before == null)
    );
  }

  private Map<String, ProvenanceChange> diff(CodeSystem left, CodeSystem right) {
    Function<CodeSystem, Map<String, Object>> fn = cs -> {
      Map<String, Object> map = JsonUtil.getObjectMapper().convertValue(cs, Map.class);
      map.put("properties", cs.getProperties() == null ? null : cs.getProperties().stream().collect(Collectors.toMap(EntityProperty::getName, x -> x)));
      map.put("identifiers", cs.getIdentifiers() == null ? null : cs.getIdentifiers().stream().collect(Collectors.toMap(Identifier::getSystem, x -> x)));
      map.put("contacts",
          cs.getContacts() == null ? null : cs.getContacts().stream().collect(Collectors.toMap(x -> x.getName() == null ? "" : x.getName(), x -> x)));
      return map;
    };
    return ProvenanceUtil.diff(fn.apply(left), fn.apply(right));
  }

//  public void provenanceCodeSystemVersion(String action, Long versionId, Consumer<CodeSystemVersion> save) {
//    CodeSystemVersion before = codeSystemVersionService.load(versionId);
//    save.accept(before);
//    CodeSystemVersion after = codeSystemVersionService.load(versionId);
//    provenanceService.create(provenance(action, after)
//        .setDetail(before == null ? null : new ProvenanceDetail().setChanges(ProvenanceUtil.diff(before, after)))
//    );
//  }

  public void provenanceCodeSystemVersion(String activity, String csId, String version, Runnable save) {
    CodeSystemVersion before = codeSystemVersionService.load(csId, version).orElse(null);
    List<String> entitiesBefore = before == null || before.getConceptsTotal() > 1000 ? null : loadEntities(before);
    save.run();
    CodeSystemVersion after = codeSystemVersionService.load(csId, version).orElseThrow();
    if (before == null) {
      provenanceService.create(provenance(activity, after).created());
      return;
    }
    Map<String, String> entitiesDiff = new HashMap<>(2);
    if (before.getConceptsTotal() <= 1000 && after.getConceptsTotal() <= 1000) {
      List<String> entitiesAfter = loadEntities(after);
      entitiesDiff.put("concepts-unlinked", String.join(", ", ListUtils.removeAll(entitiesBefore, entitiesAfter)));
      entitiesDiff.put("concepts-linked", String.join(", ", ListUtils.removeAll(entitiesAfter, entitiesBefore)));
      entitiesDiff.values().removeAll(Arrays.asList(null, ""));
    }
    provenanceService.create(provenance(activity, after).setChanges(ProvenanceUtil.diff(before, after)).setMessages(entitiesDiff));
  }

  private List<String> loadEntities(CodeSystemVersion version) {
    return version.getConceptsTotal() == 0 ? List.of() :
        codeSystemEntityVersionRepository.query(new CodeSystemEntityVersionQueryParams().setCodeSystemVersionId(version.getId()).limit(1000)).getData().stream()
            .map(x -> x.getCode() + "@" + x.getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE)).toList();
  }

  public <T> T provenanceConceptTransaction(String action, ConceptTransactionRequest req, Supplier<T> save) {
    if (req.getEntityVersion() == null) {
      return save.get();
    }
    if (req.getEntityVersion().getId() == null) {
      T result = save.get();
      provenanceService.create(provenance(action, req.getEntityVersion()).created());
      return result;
    }
    return provenanceEntityVersion(action, req.getEntityVersion().getId(), save);
  }

  public <T> T provenanceEntityVersion(String action, Long id, Supplier<T> save) {
    CodeSystemEntityVersion before = codeSystemEntityVersionService.load(id);
    T result = save.get();
    CodeSystemEntityVersion after = codeSystemEntityVersionService.load(id);
    provenanceService.create(provenance(action, after).setChanges(diff(before, after)));
    return result;
  }

  private Map<String, ProvenanceChange> diff(CodeSystemEntityVersion left, CodeSystemEntityVersion right) {
    Function<CodeSystemEntityVersion, Map<String, Object>> fn = cs -> {
      Map<String, Object> map = JsonUtil.getObjectMapper().convertValue(cs, Map.class);
      map.put("designations", cs.getDesignations() == null ? null : cs.getDesignations().stream()
          .collect(Collectors.groupingBy(Designation::getDesignationType)));
      map.put("propertyValues", cs.getPropertyValues() == null ? null : cs.getPropertyValues().stream()
          .collect(Collectors.groupingBy(EntityPropertyValue::getEntityProperty)));
      return map;
    };
    return ProvenanceUtil.diff(fn.apply(left), fn.apply(right), "versions");
  }

  public static Provenance provenance(String action, CodeSystemVersion v) {
    return new Provenance(action, "CodeSystemVersion", v.getId().toString(), v.getVersion())
        .addContext("part-of", "CodeSystem", v.getCodeSystem());
  }

  public static Provenance provenance(String action, CodeSystemEntityVersion v) {
    return new Provenance(action, "CodeSystemEntityVersion", v.getId().toString(), v.getCode())
        .addContext("part-of", "CodeSystem", v.getCodeSystem())
        .addContext("part-of", "CodeSystemEntity", v.getCodeSystemEntityId().toString());
  }


}
