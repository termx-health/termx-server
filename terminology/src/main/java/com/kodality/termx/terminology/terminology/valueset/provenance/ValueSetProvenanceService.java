package com.kodality.termx.terminology.terminology.valueset.provenance;

import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.Provenance.ProvenanceChange;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.core.sys.provenance.ProvenanceUtil;
import com.kodality.termx.terminology.terminology.valueset.ValueSetService;
import com.kodality.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetTransactionRequest;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ValueSetProvenanceService {
  private final ProvenanceService provenanceService;
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;

  public List<Provenance> find(String valueSet, String versionCode) {
    if (versionCode == null) {
      return provenanceService.find("ValueSet|" + valueSet);
    }
    return valueSetVersionService.load(valueSet, versionCode).map(vsv ->
        provenanceService.find("ValueSetVersion|" + vsv.getId().toString())
    ).orElse(List.of());
  }

  public void create(Provenance p) {
    provenanceService.create(p);
  }

  public void provenanceValueSetTransaction(String action, ValueSetTransactionRequest req, Runnable save) {
    provenanceValueSet(action, req.getValueSet().getId(), () -> {
      if (req.getVersion() != null) {
        provenanceValueSetVersion(action, req.getValueSet().getId(), req.getVersion().getVersion(), save);
        return;
      }
      save.run();
    });
  }

  public void provenanceValueSet(String action, String vsId, Runnable save) {
    ValueSet before = valueSetService.load(vsId);
    save.run();
    ValueSet after = valueSetService.load(vsId);
    provenanceService.create(new Provenance(action, "ValueSet", vsId)
        .created(before == null)
        .setChanges(before == null ? null : ProvenanceUtil.diff(before, after, "snapshots", "versions"))
    );
  }

  public void provenanceValueSetVersion(String action, String vsId, String version, Runnable save) {
    ValueSetVersion before = valueSetVersionService.load(vsId, version).orElse(null);
    save.run();
    ValueSetVersion after = valueSetVersionService.load(vsId, version).orElseThrow();
    provenanceService.create(new Provenance(action, "ValueSetVersion", after.getId().toString(), after.getVersion())
            .created(before == null)
        .addContext("part-of", "ValueSet", after.getValueSet())
        .setChanges(before == null ? null : diff(before, after))
    );
  }

  private Map<String, ProvenanceChange> diff(ValueSetVersion left, ValueSetVersion right) {
    Consumer<ValueSetVersion> fn = vs -> {
      if (vs.getRuleSet() == null || vs.getRuleSet().getRules() == null) {
        return;
      }
      vs.getRuleSet().getRules().forEach(r -> {
        if (r.getConcepts() != null) {
          r.getConcepts().forEach(c -> c.setConcept(new ValueSetVersionConceptValue().setCode(c.getConcept().getCode())));
        }
      });
    };
    fn.accept(right);
    fn.accept(left);
    return ProvenanceUtil.diff(left, right, "snapshot");
  }
}
