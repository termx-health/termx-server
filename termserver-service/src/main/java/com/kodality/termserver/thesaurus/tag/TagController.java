package com.kodality.termserver.thesaurus.tag;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller("/tags")
public class TagController {

  private final TagService tagService;

  @Get
  public List<Tag> getAll() {
    return tagService.loadAll();
  }
}
