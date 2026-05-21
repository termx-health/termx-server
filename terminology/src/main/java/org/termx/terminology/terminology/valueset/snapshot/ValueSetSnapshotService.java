package org.termx.terminology.terminology.valueset.snapshot;

import org.termx.core.auth.SessionStore;
import org.termx.ts.valueset.ValueSetSnapshot;
import org.termx.ts.valueset.ValueSetVersionConcept;
import org.termx.ts.valueset.ValueSetVersionReference;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.termx.ts.valueset.ValueSetSnapshotDependency;

@Singleton
@RequiredArgsConstructor
public class ValueSetSnapshotService {
  private final ValueSetSnapshotRepository repository;

  public ValueSetSnapshot createSnapshot(String valueSet, Long versionId, List<ValueSetVersionConcept> expansion) {
    return createSnapshot(valueSet, versionId, expansion, null);
  }

  public ValueSetSnapshot createSnapshot(String valueSet, Long versionId, List<ValueSetVersionConcept> expansion,
                                         List<ValueSetSnapshotDependency> dependencies) {
    if (valueSet == null || versionId == null || expansion == null) {
      return null;
    }
    ValueSetSnapshot snapshot = load(valueSet, versionId);
    if (snapshot == null) {
      snapshot = new ValueSetSnapshot().setValueSet(valueSet).setValueSetVersion(new ValueSetVersionReference().setId(versionId));
    }
    snapshot.setExpansion(expansion);
    snapshot.setDependencies(dependencies);
    snapshot.setConceptsTotal(expansion.size());
    snapshot.setCreatedAt(OffsetDateTime.now());
    snapshot.setCreatedBy(SessionStore.require().getUsername());
    repository.save(snapshot);

    return snapshot;
  }

  public ValueSetSnapshot load(String valueSet, Long versionId) {
    return repository.load(valueSet, versionId);
  }

  /**
   * Returns ONLY the saved snapshot's {@code concepts_total} — a single int — without
   * loading the (potentially large) expansion list. Used by the FHIR read flow on
   * {@code ?_summary=false} to populate {@code ValueSet.expansion.total} without
   * shipping the full expansion contents.
   *
   * @return concept count of the saved snapshot, or {@code null} if no snapshot exists
   */
  public Integer loadConceptsTotal(String valueSet, Long versionId) {
    return repository.loadConceptsTotal(valueSet, versionId);
  }
}
