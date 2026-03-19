package org.termx.auth;

import java.util.Map;
import java.util.Set;

public interface OAuthSessionPrivilegeMapper {
    Set<String> getPrivileges(Map<String, Object> json);
}
