package com.kodality.termserver.terminology.relatedartifacts;

import com.kodality.termserver.ts.relatedartifact.RelatedArtifact;
import com.kodality.termserver.ts.relatedartifact.RelatedArtifactRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("related-artifacts")
@RequiredArgsConstructor
public class RelatedArtifactController {
  private final List<RelatedArtifactService> services;

  @Post()
  public List<RelatedArtifact> findRelatedArtifacts(@Valid @Body RelatedArtifactRequest request) {
    return services.stream().flatMap(s -> s.findRelatedArtifacts(request).stream()).toList();
  }
}
