package com.kodality.termserver.sys.spacepackage;

import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.sys.spacepackage.version.PackageVersionService;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Controller("/packages")
@RequiredArgsConstructor
public class PackageController {
  private final PackageService packageService;
  private final PackageVersionService packageVersionService;

  @Authorized(Privilege.P_VIEW)
  @Get("/{id}")
  public Package load(@Parameter Long id) {
    return packageService.load(id);
  }

  @Authorized(Privilege.P_VIEW)
  @Get("/{id}/versions")
  public List<PackageVersion> loadVersions(@Parameter Long id) {
    return packageVersionService.loadAll(id);
  }

  @Authorized(Privilege.P_EDIT)
  @Delete("/{id}")
  public HttpResponse<?> delete(@PathVariable Long id) {
    packageService.delete(id);
    return HttpResponse.ok();
  }
}
