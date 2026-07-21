package org.termx.auth;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
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

    /**
     * Comma-separated, dot-separated claim paths holding role names, tried in order and unioned.
     *
     * <p>Defaults to the flat {@code roles} claim, so existing deployments are unaffected. Keycloak
     * puts realm roles at {@code realm_access.roles} and client roles at
     * {@code resource_access.<client>.roles}; a deployment on either sets this rather than
     * implementing its own mapper.
     *
     * <p>Bound as a plain String and split here rather than as a {@code List<String>}: this is the
     * authentication path, and a binding that silently yielded an empty list would strip every
     * user's privileges. Splitting explicitly keeps that behaviour visible and testable.
     */
    @Value("${auth.privilege.mapper.role-claims:roles}")
    String roleClaims;

    @Override
    public Set<String> getPrivileges(Map<String, Object> json) {
        return privilegeStore.getPrivileges(RoleClaimReader.readRoles(json, RoleClaimReader.parsePaths(roleClaims)));
    }
}
