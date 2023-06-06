package com.kodality.termserver.terminology.project.projectpackage.version;

import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.ts.project.projectpackage.PackageVersion;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/package-versions")
@RequiredArgsConstructor
public class PackageVersionController {
  private final PackageVersionService packageVersionService;

  @Authorized(Privilege.P_VIEW)
  @Get("{id}")
  public PackageVersion load(@Parameter Long id) {
    return packageVersionService.load(id);
  }

  @Authorized(Privilege.P_EDIT)
  @Delete("/{id}")
  public HttpResponse<?> delete(@PathVariable Long id) {
    packageVersionService.delete(id);
    return HttpResponse.ok();
  }
}
