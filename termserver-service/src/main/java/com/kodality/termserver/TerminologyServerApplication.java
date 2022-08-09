package com.kodality.termserver;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@OpenAPIDefinition(
    info = @Info(
        title = "KTS",
        description = "${openapi.description}",
        contact = @Contact(url = "https://kodality.com", name = "Kodality support", email = "support@kodality.com")
    ),
    externalDocs = @ExternalDocumentation(description = "Read more about KTS", url = "https://wiki.kodality.dev/en/terminology-server")
)
@SecurityScheme(name = "openid",
    type = SecuritySchemeType.OAUTH2,
    scheme = "bearer",
    bearerFormat = "jwt",
    flows = @OAuthFlows(
        authorizationCode = @OAuthFlow(
            authorizationUrl = "${oauth.url}/protocol/openid-connect/auth",
            tokenUrl = "${oauth.url}/protocol/openid-connect/token",
            scopes = @OAuthScope(name = "openid", description = "OpenID scope")
        )
    )
)
@SecurityRequirement(name = "openid")
public class TerminologyServerApplication {

  public static void main(String[] args) {
    Micronaut.run(TerminologyServerApplication.class, args);
  }
}

