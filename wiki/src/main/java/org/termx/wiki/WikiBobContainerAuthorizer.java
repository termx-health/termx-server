package org.termx.wiki;

import org.termx.bob.BobContainerAuthorizer;
import org.termx.bob.BobObject;
import org.termx.core.auth.SessionInfo;
import jakarta.inject.Singleton;

/**
 * Authorizes access to Wiki attachments stored under the {@code "wiki"} Bob container
 * when reached via the generic {@code /bob/objects} REST API.
 *
 * <p>The fine-grained per-space checks still live on the {@code /pages/{id}/files}
 * endpoints; this authorizer only governs the cross-cutting Bob API and therefore
 * requires the broad {@code *.Wiki.read} / {@code *.Wiki.write} privileges — i.e. only
 * an admin-style user (with permission on any space) can list, fetch, or mutate Wiki
 * attachments through {@code /bob/objects}. Per-space callers should continue using the
 * existing {@code PageController} endpoints.</p>
 */
@Singleton
public class WikiBobContainerAuthorizer implements BobContainerAuthorizer {

  @Override
  public String getContainer() {
    return "wiki";
  }

  @Override
  public void checkRead(SessionInfo session, BobObject object) {
    session.checkPermitted("*", Privilege.W_READ);
  }

  @Override
  public void checkWrite(SessionInfo session, BobObject object) {
    session.checkPermitted("*", Privilege.W_WRITE);
  }
}
