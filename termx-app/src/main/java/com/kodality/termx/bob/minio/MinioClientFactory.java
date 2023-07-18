package com.kodality.termx.bob.minio;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.minio.MinioClient;


@Factory
@Requires(property = "bob.minio.url")
public class MinioClientFactory {
  @Value("${bob.minio.url}")
  private String minioUrl;
  @Value("${bob.minio.access-key}")
  private String minioAccessKey;
  @Value("${bob.minio.secret-key}")
  private String minioSecretKey;

  @Bean
  public MinioClient getMinioClient() {
    return MinioClient.builder()
        .endpoint(minioUrl)
        .credentials(minioAccessKey, minioSecretKey)
        .build();
  }
}
