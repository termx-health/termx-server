package org.termx.auth;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
@Requires(property = "auth.privilege.mapper.direct")
public class DirectOAuthSessionPrivilegeMapper implements OAuthSessionPrivilegeMapper {
    @Value("${auth.privilege.mapper.direct}")
    private String directPrivilegeMapping;

    @Override
    public Set<String> getPrivileges(Map<String, Object> json) {
        return new HashSet<>((List<String>) json.getOrDefault(directPrivilegeMapping, List.of()));
    }
}
