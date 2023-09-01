package com.kodality.termx.terminology.mapset;

import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.sys.provenance.Provenance.ProvenanceChange;
import com.kodality.termx.sys.provenance.ProvenanceService;
import com.kodality.termx.sys.provenance.ProvenanceUtil;
import com.kodality.termx.terminology.mapset.version.MapSetVersionService;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetTransactionRequest;
import com.kodality.termx.ts.mapset.MapSetVersion;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class MapSetProvenanceService {
  private final ProvenanceService provenanceService;
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;

  public void create(Provenance p) {
    provenanceService.create(p);
  }

  public void provenanceMapSetTransaction(String action, MapSetTransactionRequest req, Runnable save) {
    provenanceMapSet(action, req.getMapSet().getId(), () -> {
      if (req.getVersion() != null) {
        provenanceMapSetVersion(action, req.getMapSet().getId(), req.getVersion().getVersion(), save);
        return;
      }
      save.run();
    });
  }

  public void provenanceMapSet(String action, String msId, Runnable save) {
    MapSet before = mapSetService.load(msId).orElse(null);
    save.run();
    MapSet after = mapSetService.load(msId).orElse(null);
    provenanceService.create(new Provenance(action, "MapSet", msId)
        .created(before == null)
        .setChanges(before == null ? null : ProvenanceUtil.diff(before, after, "versions"))
    );
  }

  public void provenanceMapSetVersion(String action, String msId, String version, Runnable save) {
    MapSetVersion before = mapSetVersionService.load(msId, version).orElse(null);
    save.run();
    MapSetVersion after = mapSetVersionService.load(msId, version).orElseThrow();
    provenanceService.create(new Provenance(action, "MapSetVersion", after.getId().toString(), after.getVersion())
            .created(before == null)
        .addContext("part-of", "MapSet", after.getMapSet())
        .setChanges(before == null ? null : diff(before, after))
    );
  }

  private Map<String, ProvenanceChange> diff(MapSetVersion left, MapSetVersion right) {
    return ProvenanceUtil.diff(left, right, "statistics");
  }
}
