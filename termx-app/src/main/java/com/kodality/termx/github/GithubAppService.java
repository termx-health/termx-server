package com.kodality.termx.github;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.kodality.commons.cache.CacheManager;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpHeaders;
import jakarta.inject.Singleton;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.PrivateKey;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

@Singleton
public class GithubAppService {

  private final HttpClient http;
  private final RSAPrivateKey key;
  private final CacheManager cacheManager;
  private final String applicationId;

  public GithubAppService(
      @Value("${github.app-id}") String applicationId,
      @Value("${github.private-key-path}") String privateKeyPath
  ) throws Exception {
    this.applicationId = applicationId;
    this.cacheManager = new CacheManager();
    this.cacheManager.initCache("github-jwt", 1, TimeUnit.MINUTES.toSeconds(5));

    Security.addProvider(new BouncyCastleProvider());
    this.key = (RSAPrivateKey) loadKey(privateKeyPath);
    this.http = HttpClient.newBuilder().build();
  }

  public String getApp() {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .GET()
          .uri(new URI("https://api.github.com/app"))
          .setHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
          .setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + getJwt())
          .build();
      HttpResponse<String> response = this.http.send(request, BodyHandlers.ofString());
      return response.body();
    } catch (IOException | InterruptedException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private String getJwt() {
    return cacheManager.get("github-jwt", "jwt", () ->
        JWT.create()
            .withIssuer(applicationId)
            .withIssuedAt(OffsetDateTime.now().toInstant())
            .withExpiresAt(OffsetDateTime.now().plusMinutes(10).toInstant())
            .sign(Algorithm.RSA256(key)));
  }

  private static PrivateKey loadKey(String privateKeyPath) throws IOException {
    PEMParser pemParser = new PEMParser(new FileReader(privateKeyPath));
    return new JcaPEMKeyConverter().setProvider("BC").getKeyPair((PEMKeyPair) pemParser.readObject()).getPrivate();
  }
}
