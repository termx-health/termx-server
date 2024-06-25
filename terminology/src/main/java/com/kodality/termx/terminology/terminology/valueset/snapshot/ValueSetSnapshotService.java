package com.kodality.termx.terminology.terminology.valueset.snapshot;

import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.ts.valueset.ValueSetSnapshot;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionReference;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ValueSetSnapshotService {
  private final ValueSetSnapshotRepository repository;

  public void createSnapshot(String valueSet, Long versionId, List<ValueSetVersionConcept> expansion) {
    if (valueSet == null || versionId == null || expansion == null) {
      return;
    }
    ValueSetSnapshot snapshot = load(valueSet, versionId);
    if (snapshot == null) {
      snapshot = new ValueSetSnapshot().setValueSet(valueSet).setValueSetVersion(new ValueSetVersionReference().setId(versionId));
    }
    snapshot.setExpansion(expansion);
    snapshot.setConceptsTotal(expansion.size());
    snapshot.setCreatedAt(OffsetDateTime.now());
    snapshot.setCreatedBy(SessionStore.require().getUsername());
    repository.save(snapshot);
  }

  public ValueSetSnapshot load(String valueSet, Long versionId) {
    return repository.load(valueSet, versionId);
  }
}
