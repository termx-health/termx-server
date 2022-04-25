package com.kodality.termserver.association;

import com.kodality.termserver.commons.model.exception.NotFoundException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/association-types")
@RequiredArgsConstructor
public class AssociationTypeController {
  private final AssociationTypeService associationTypeService;

  @Get(uri = "/{code}")
  public AssociationType getCodeSystem(@PathVariable String code) {
    return associationTypeService.load(code).orElseThrow(() -> new NotFoundException("Association type not found: " + code));
  }

  @Post
  public HttpResponse<?> create(@Body @Valid AssociationType associationType) {
    associationTypeService.save(associationType);
    return HttpResponse.created(associationType);
  }

  @Put("/{code}")
  public HttpResponse<?> update(@PathVariable String code, @Body @Valid AssociationType associationType) {
    associationType.setCode(code);
    associationTypeService.save(associationType);
    return HttpResponse.ok();
  }
}
