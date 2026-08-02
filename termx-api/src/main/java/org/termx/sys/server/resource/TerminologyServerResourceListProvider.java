package org.termx.sys.server.resource;

import org.termx.sys.server.TerminologyServer;
import org.termx.sys.server.TerminologyServer.AuthoritativeResource;
import java.util.List;

/**
 * Lists resources that live on a <b>remote</b> terminology server (as opposed to the local installation's
 * database), filtered by the server's authoritative URL patterns. Used to populate the server "Resources"
 * view for non-current-installation servers.
 */
public interface TerminologyServerResourceListProvider {
  /** @param type one of {@code code-systems|value-sets|concept-maps|structure-definitions|structure-maps} */
  boolean checkType(String type);

  List<AuthoritativeResource> list(TerminologyServer server, String type, List<AuthoritativeResource> patterns);
}
