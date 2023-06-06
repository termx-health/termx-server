package com.kodality.termserver.thesaurus.tag;

import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.thesaurus.Privilege;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller("/tags")
public class TagController {

  private final TagService tagService;

  @Authorized(Privilege.T_VIEW)
  @Get
  public List<Tag> getAll() {
    return tagService.loadAll();
  }
}
