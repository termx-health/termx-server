package com.kodality.termx.wiki.tag;

import com.kodality.termx.auth.Authorized;
import com.kodality.termx.wiki.Privilege;
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
