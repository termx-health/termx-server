package com.kodality.termserver.ts.association;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.association.AssociationTypeQueryParams;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.auth.auth.ResourceId;
import com.kodality.termserver.auth.auth.UserPermissionService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/ts/association-types")
@RequiredArgsConstructor
public class AssociationTypeController {
  private final AssociationTypeService associationTypeService;
  private final UserPermissionService userPermissionService;

  @Authorized("*.AssociationType.view")
  @Get(uri = "{?params*}")
  public QueryResult<AssociationType> queryAssociationTypes(AssociationTypeQueryParams params) {
    params.setPermittedCodes(userPermissionService.getPermittedResourceIds("AssociationType", "view"));
    return associationTypeService.query(params);
  }

  @Authorized("*.AssociationType.view")
  @Get(uri = "/{code}")
  public AssociationType getAssociationType(@PathVariable @ResourceId String code) {
    return associationTypeService.load(code).orElseThrow(() -> new NotFoundException("Association type not found: " + code));
  }

  @Authorized("*.AssociationType.edit")
  @Post
  public HttpResponse<?> createAssociationType(@Body @Valid AssociationType associationType) {
    associationTypeService.save(associationType);
    return HttpResponse.created(associationType);
  }

  @Authorized("*.AssociationType.edit")
  @Put("/{code}")
  public HttpResponse<?> updateAssociationType(@PathVariable @ResourceId String code, @Body @Valid AssociationType associationType) {
    associationType.setCode(code);
    associationTypeService.save(associationType);
    return HttpResponse.ok();
  }
}
