package org.termx.auth;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.termx.uam.privilege.PrivilegeStore;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Singleton
@Requires(property = "auth.privilege.mapper.store", notEquals = "disabled")
public class PrivilegeStoreOAuthSessionPrivilegeMapper implements OAuthSessionPrivilegeMapper {
    private final PrivilegeStore privilegeStore;

    @Override
    public Set<String> getPrivileges(Map<String, Object> json) {
        return privilegeStore.getPrivileges((List<String>) json.get("roles"));
    }
}
