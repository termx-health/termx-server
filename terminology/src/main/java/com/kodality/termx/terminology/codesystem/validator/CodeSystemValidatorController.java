package com.kodality.termx.terminology.codesystem.validator;

import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.sys.lorque.LorqueProcess;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/ts/code-systems/validator")
@RequiredArgsConstructor
public class CodeSystemValidatorController {
  private final CodeSystemUniquenessValidatorService uniquenessValidatorService;
  private final CodeSystemCircularDependenciesDetectorService circularDependenciesDetectorService;

  @Authorized(Privilege.CS_VIEW)
  @Post("/validate-uniqueness")
  public HttpResponse<?> validateUniqueness(@Body @Valid CodeSystemUniquenessValidatorRequest request) {
    LorqueProcess lorqueProcess = uniquenessValidatorService.validate(request);
    return HttpResponse.accepted().body(lorqueProcess);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get("/detect-circular-dependencies")
  public CodeSystemCircularDependenciesDetectorResult detectCircularDependencies(@QueryValue Long versionId) {
    return circularDependenciesDetectorService.detect(versionId);
  }

}