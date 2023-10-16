package com.kodality.termx.terminology.terminology.association;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.association.AssociationTypeQueryParams;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
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
  private final AssociationTypeDeleteService associationTypeDeleteService;

  @Authorized(Privilege.AT_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<AssociationType> queryAssociationTypes(AssociationTypeQueryParams params) {
    params.setPermittedCodes(SessionStore.require().getPermittedResourceIds(Privilege.AT_VIEW));
    return associationTypeService.query(params);
  }

  @Authorized(Privilege.AT_VIEW)
  @Get(uri = "/{code}")
  public AssociationType getAssociationType(@PathVariable String code) {
    return associationTypeService.load(code).orElseThrow(() -> new NotFoundException("Association type not found: " + code));
  }

  @Authorized(Privilege.AT_EDIT)
  @Post
  public HttpResponse<?> createAssociationType(@Body @Valid AssociationType associationType) {
    SessionStore.require().checkPermitted(associationType.getCode(), Privilege.AT_EDIT);
    associationTypeService.save(associationType);
    return HttpResponse.created(associationType);
  }

  @Authorized(Privilege.AT_EDIT)
  @Put("/{code}")
  public HttpResponse<?> updateAssociationType(@PathVariable String code, @Body @Valid AssociationType associationType) {
    associationType.setCode(code);
    associationTypeService.save(associationType);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.AT_PUBLISH)
  @Delete(uri = "/{code}")
  public HttpResponse<?> deleteAssociationType(@PathVariable String code) {
    associationTypeDeleteService.delete(code);
    return HttpResponse.ok();
  }
}
