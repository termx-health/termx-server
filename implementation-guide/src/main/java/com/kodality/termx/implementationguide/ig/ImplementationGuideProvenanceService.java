package com.kodality.termx.implementationguide.ig;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.Provenance.ProvenanceChange;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.core.sys.provenance.ProvenanceUtil;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersion;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersionService;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ImplementationGuideProvenanceService {
  private final ProvenanceService provenanceService;
  private final ImplementationGuideService igService;
  private final ImplementationGuideVersionService igVersionService;

  public List<Provenance> find(String ig, String versionCode) {
    if (versionCode == null) {
      return provenanceService.find("ImplementationGuide|" + versionCode);
    }
    return igVersionService.load(ig, versionCode).map(csv ->
        provenanceService.find("ImplementationGuideVersion|" + csv.getId().toString())
    ).orElse(List.of());
  }

  public void create(Provenance p) {
    provenanceService.create(p);
  }

  public void provenanceImplementationGuideTransactionRequest(String action, ImplementationGuideTransactionRequest request, Runnable save) {
    provenanceImplementationGuide(action, request.getImplementationGuide().getId(), () -> {
      if (request.getVersion() != null) {
        provenanceImplementationGuideVersion(action, request.getImplementationGuide().getId(), request.getVersion().getVersion(), save);
        return;
      }
      save.run();
    });
  }

  public void provenanceImplementationGuide(String action, String igId, Runnable save) {
    ImplementationGuide before = igService.load(igId).orElse(null);
    save.run();
    ImplementationGuide after = igService.load(igId).orElseThrow();
    provenanceService.create(new Provenance(action, "ImplementationGuide", igId)
        .setChanges(before == null ? null : diff(before, after))
        .created(before == null)
    );
  }

  private Map<String, ProvenanceChange> diff(ImplementationGuide left, ImplementationGuide right) {
    Function<ImplementationGuide, Map<String, Object>> fn = ig -> {
      Map<String, Object> map = JsonUtil.getObjectMapper().convertValue(ig, Map.class);
      map.put("identifiers", ig.getIdentifiers() == null ? null : ig.getIdentifiers().stream().collect(Collectors.toMap(Identifier::getSystem, x -> x)));
      map.put("contacts",
          ig.getContacts() == null ? null : ig.getContacts().stream().collect(Collectors.toMap(x -> x.getName() == null ? "" : x.getName(), x -> x)));
      return map;
    };
    return ProvenanceUtil.diff(fn.apply(left), fn.apply(right));
  }

  public void provenanceImplementationGuideVersion(String activity, String csId, String version, Runnable save) {
    ImplementationGuideVersion before = igVersionService.load(csId, version).orElse(null);
    save.run();
    ImplementationGuideVersion after = igVersionService.load(csId, version).orElseThrow();
    if (before == null) {
      provenanceService.create(provenance(activity, after).created());
      return;
    }
    provenanceService.create(provenance(activity, after).setChanges(ProvenanceUtil.diff(before, after)));
  }

  public static Provenance provenance(String action, ImplementationGuideVersion v) {
    return new Provenance(action, "ImplementationGuideVersion", v.getId().toString(), v.getVersion())
        .addContext("part-of", "ImplementationGuide", v.getImplementationGuide());
  }

}
