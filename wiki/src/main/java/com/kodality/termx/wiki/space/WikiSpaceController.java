package com.kodality.termx.wiki.space;

import com.kodality.commons.model.CodeName;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.sys.space.SpaceQueryParams;
import com.kodality.termx.core.sys.space.SpaceService;
import com.kodality.termx.wiki.Privilege;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Controller("/wiki-spaces")
@RequiredArgsConstructor
public class WikiSpaceController {
  private final SpaceService spaceService;

  @Authorized(Privilege.W_VIEW)
  @Get()
  public List<CodeName> listSpaces() {
    SpaceQueryParams params = new SpaceQueryParams();
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.W_VIEW, Long::valueOf));
    params.all();
    return spaceService.query(params).getData().stream()
        .map(s -> new CodeName(s.getId(), s.getCode(), s.getNames()))
        .toList();
  }
}
