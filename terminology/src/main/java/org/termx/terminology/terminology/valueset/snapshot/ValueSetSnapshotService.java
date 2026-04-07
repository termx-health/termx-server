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
}
