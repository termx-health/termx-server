package org.termx.wiki.importer;

import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionStore;
import org.termx.core.sys.provenance.Provenance;
import org.termx.core.sys.provenance.ProvenanceService;
import org.termx.wiki.Privilege;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@Controller("/wiki-import")
@RequiredArgsConstructor
public class WikiImportController {
  private final WikiGithubImportService importService;
  private final ProvenanceService provenanceService;

  /** Import a Space's pages directly from a GitHub repository (no git integration required). */
  @Authorized(Privilege.W_WRITE)
  @Post("/github")
  public WikiGithubImportResult importFromGithub(@Body @Valid WikiGithubImportRequest request) {
    SessionStore.require().checkPermitted(request.getSpaceId().toString(), Privilege.W_WRITE);
    WikiGithubImportResult result = importService.importSpace(request);
    provenanceService.create(new Provenance("modified", "Space", request.getSpaceId().toString()));
    return result;
  }
}
