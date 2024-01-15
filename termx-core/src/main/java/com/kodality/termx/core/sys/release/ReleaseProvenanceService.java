package com.kodality.termx.core.sys.release;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.Provenance.ProvenanceChange;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.core.sys.provenance.ProvenanceUtil;
import com.kodality.termx.sys.release.Release;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ReleaseProvenanceService {
  private final ReleaseService releaseService;
  private final ProvenanceService provenanceService;

  public List<Provenance> find(Long releaseId) {
    String code = releaseService.load(releaseId).getCode();
    return provenanceService.find("Release|" + code);
  }

  public void provenanceRelease(String action, String code, Runnable save) {
    Release before = releaseService.load(code);
    save.run();
    Release after = releaseService.load(code);
    provenanceService.create(new Provenance(action, "Release", code)
        .setChanges(before == null ? null : diff(before, after))
        .created(before == null)
    );
  }

  public void provenanceRelease(String action, Long id, Runnable save) {
    Release before = releaseService.load(id);
    save.run();
    Release after = releaseService.load(id);
    provenanceService.create(new Provenance(action, "Release", after.getCode())
        .setChanges(before == null ? null : diff(before, after))
        .created(before == null)
    );
  }

  private Map<String, ProvenanceChange> diff(Release left, Release right) {
    Function<Release, Map<String, Object>> fn = r -> JsonUtil.getObjectMapper().convertValue(r, Map.class);
    return ProvenanceUtil.diff(fn.apply(left), fn.apply(right));
  }

}
