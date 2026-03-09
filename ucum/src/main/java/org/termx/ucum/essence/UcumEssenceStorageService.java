package org.termx.ucum.essence;

import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class UcumEssenceStorageService {
  private final UcumEssenceRepository repository;

  public Optional<UcumEssence> loadActive() {
    return Optional.ofNullable(repository.loadActive());
  }

  @Transactional
  public UcumEssence activate(String version, String xml) {
    repository.cancelActive();
    UcumEssence essence = new UcumEssence()
        .setVersion(version)
        .setXml(xml);
    repository.save(essence);
    return essence;
  }
}
