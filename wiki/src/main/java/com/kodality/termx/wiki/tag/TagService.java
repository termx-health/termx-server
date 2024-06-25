package com.kodality.termx.wiki.tag;

import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TagService {
  private final TagRepository repository;

  public List<Tag> loadAll() {
    return repository.loadAll();
  }

  public Tag load(Long id) {
    return repository.load(id);
  }

  public void save(Tag tag) {
    repository.save(tag);
  }
}
