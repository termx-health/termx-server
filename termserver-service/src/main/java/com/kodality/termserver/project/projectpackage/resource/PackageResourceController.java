package com.kodality.termserver.project.projectpackage.resource;

import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.project.projectpackage.PackageVersion.PackageResource;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Controller("/package-resources")
@RequiredArgsConstructor
public class PackageResourceController {
  private final PackageResourceService packageResourceService;

  @Authorized("*.Project.view")
  @Get("/{?params*}")
  public List<PackageResource> loadAll(Map<String, String> params) {
    return packageResourceService.loadAll(params.get("projectCode"), params.get("packageCode"), params.get("version"));
  }

  @Authorized("*.Project.edit")
  @Put("{id}")
  public PackageResource update(@Parameter Long id, @Valid @Body PackageResourceSaveRequest request) {
    request.getResource().setId(id);
    return packageResourceService.save(request.getVersionId(), request.getResource());
  }

  @Getter
  @Setter
  @Introspected
  public static class PackageResourceSaveRequest {
    @NotNull
    private Long versionId;
    @NotNull
    private PackageResource resource;
  }
}
