package com.kodality.termx.terminology.terminology.relatedartifacts;

import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.ts.relatedartifact.RelatedArtifact;
import com.kodality.termx.ts.relatedartifact.RelatedArtifactRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("related-artifacts")
@RequiredArgsConstructor
public class RelatedArtifactController {
  private final List<RelatedArtifactService> services;

  @Authorized("*.*.view")
  @Post()
  public List<RelatedArtifact> findRelatedArtifacts(@Valid @Body RelatedArtifactRequest request) {
    return services.stream().flatMap(s -> s.findRelatedArtifacts(request).stream()).toList();
  }
}
