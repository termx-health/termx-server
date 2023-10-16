package com.kodality.termx.core.sys.spacepackage;

import com.kodality.termx.core.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.sys.spacepackage.Package;
import com.kodality.termx.sys.spacepackage.PackageTransactionRequest;
import com.kodality.termx.sys.spacepackage.PackageVersion;
import com.kodality.termx.core.sys.spacepackage.version.PackageVersionService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Controller("/spaces/{spaceId}/packages")
@RequiredArgsConstructor
public class PackageController {
  private final PackageService packageService;
  private final PackageVersionService packageVersionService;

  @Authorized(Privilege.S_VIEW)
  @Get()
  public List<Package> loadPackages(@PathVariable Long spaceId) {
    return packageService.loadAll(spaceId);
  }

  @Authorized(Privilege.S_EDIT)
  @Post()
  public Package savePackage(@PathVariable Long spaceId, @Body PackageTransactionRequest request) {
    return packageService.save(request, spaceId);
  }

  @Authorized(Privilege.S_VIEW)
  @Get("/{id}")
  public Package load(@PathVariable Long spaceId, @PathVariable Long id) {
    return packageService.load(spaceId, id);
  }

  @Authorized(Privilege.S_VIEW)
  @Get("/{id}/versions")
  public List<PackageVersion> loadVersions(@PathVariable Long spaceId, @PathVariable Long id) {
    return packageVersionService.loadAll(spaceId, id);
  }

  @Authorized(Privilege.S_VIEW)
  @Get("/{id}/versions/{versionId}")
  public PackageVersion loadVersion(@PathVariable Long spaceId, @PathVariable Long id, @PathVariable Long versionId) {
    return packageVersionService.load(spaceId, id, versionId);
  }

  @Authorized(Privilege.S_EDIT)
  @Delete("/{id}")
  public HttpResponse<?> delete(@PathVariable Long spaceId, @PathVariable Long id) {
    packageService.delete(spaceId, id);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.S_EDIT)
  @Delete("/{id}/versions/{versionId}")
  public HttpResponse<?> deleteVersion(@PathVariable Long spaceId, @PathVariable Long id, @PathVariable Long versionId) {
    packageVersionService.delete(spaceId, id, versionId);
    return HttpResponse.ok();
  }
}
