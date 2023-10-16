package com.kodality.termx.core.sys.provenance;

import com.kodality.commons.model.Reference;
import com.kodality.termx.core.auth.SessionStore;
import java.time.OffsetDateTime;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ProvenanceService {
  private final ProvenanceRepository repository;

  @Transactional
  public void create(Provenance provenance) {
    provenance.setDate(provenance.getDate() == null ? OffsetDateTime.now() : provenance.getDate());
    provenance.setAuthor(provenance.getAuthor() == null ? new Reference("user", SessionStore.require().getUsername()) : provenance.getAuthor());
    repository.create(provenance);
  }

  public List<Provenance> find(String target) {
    return repository.find(target);
  }

}

