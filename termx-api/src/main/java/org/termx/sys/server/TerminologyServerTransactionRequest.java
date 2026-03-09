package org.termx.sys.server;

import org.termx.sys.server.TerminologyServer.TerminologyServerAuthConfig;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class TerminologyServerTransactionRequest {
  private TerminologyServer ts;
  private TerminologyServerAuthConfig authConfig;
}
