package org.termx.wiki.space;

import com.kodality.commons.model.LocalizedName;
import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionStore;
import org.termx.core.sys.space.SpaceService;
import org.termx.sys.space.Space;
import org.termx.sys.space.SpaceQueryParams;
import org.termx.wiki.Privilege;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Controller("/wiki-spaces")
@RequiredArgsConstructor
public class WikiSpaceController {
  private final SpaceService spaceService;

  @Authorized(Privilege.W_VIEW)
  @Get()
  public List<WikiSpace> listSpaces() {
    SpaceQueryParams params = new SpaceQueryParams();
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.W_VIEW, Long::valueOf));
    params.all();
    return spaceService.query(params).getData().stream()
        .map(WikiSpace::fromSpace)
        .toList();
  }


  @Getter
  @Setter
  @Accessors(chain = true)
  public static class WikiSpace {
    private Long id;
    private String code;
    private LocalizedName names;
    private boolean active;

    public static WikiSpace fromSpace(Space s) {
      return new WikiSpace()
          .setId(s.getId())
          .setCode(s.getCode())
          .setNames(s.getNames())
          .setActive(s.isActive());
    }
  }
}
