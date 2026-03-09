package org.termx.terminology.terminology.mapset.version;

import org.termx.terminology.Privilege;
import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionStore;
import org.termx.ts.mapset.MapSetVersion;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/map-set-versions")
@RequiredArgsConstructor
public class MapSetVersionController {
  private final MapSetVersionService mapSetVersionService;

  @Authorized(privilege = Privilege.VS_VIEW)
  @Get(uri = "/{id}")
  public MapSetVersion getVersion(@PathVariable Long id) {
    MapSetVersion version = mapSetVersionService.load(id);
    SessionStore.require().checkPermitted(version.getMapSet(), Privilege.VS_VIEW);
    return version;
  }
}
