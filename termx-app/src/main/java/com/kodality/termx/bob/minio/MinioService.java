package com.kodality.termx.bob.minio;

import com.kodality.termx.bob.BobObject;
import com.kodality.termx.bob.BobStorage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.types.files.StreamedFile;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;

import static java.lang.String.format;

@Singleton
@Slf4j
@RequiredArgsConstructor
@Requires(bean = MinioClient.class)
public class MinioService {
  private final MinioClient minioClient;

  public void store(BobObject object, byte[] content) {
    BobStorage storage = object.getStorage();
    checkBucketExists(storage.getContainer());
    String fullPath = storage.getFullPath();

    try {
      StatObjectResponse statObject = minioClient.statObject(
          StatObjectArgs.builder()
              .bucket(storage.getContainer())
              .object(fullPath)
              .build());
      log.info("File '{}' already exists in bucket '{}' (etag='{}', size='{}'), skipping it.", fullPath, storage.getContainer(), statObject.etag(), statObject.size());
    } catch (ErrorResponseException e) {
      try (var r = e.response()) {
        if (r.code() == 404) {
          log.info("File '{}' does not exist in bucket '{}', uploading it.", fullPath, storage.getContainer());
          uploadFile(object, content);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to check file existence", e);
    }
  }

  public StreamedFile retrieve(BobObject object) {
    BobStorage storage = object.getStorage();
    try {
      return new StreamedMediaFile(minioClient.getObject(
          GetObjectArgs.builder()
              .bucket(storage.getContainer())
              .object(storage.getFullPath())
              .build()));
    } catch (Exception e) {
      throw new RuntimeException(format("Failed to retrieve object '%s' from bucket '%s", storage.getFullPath(), storage.getContainer()), e);
    }
  }


  private void checkBucketExists(String bucket) {
    try {
      if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
        log.info("Bucket '{}' does not exist, creating it.", bucket);
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to check bucket existence", e);
    }
  }

  private void uploadFile(BobObject object, byte[] content) {
    try {
      PutObjectArgs req = PutObjectArgs.builder()
          .bucket(object.getStorage().getContainer())
          .object(object.getStorage().getFullPath())
          .contentType(object.getContentType())
          .stream(new ByteArrayInputStream(content), content.length, -1)
          .build();
      minioClient.putObject(req);
    } catch (Exception e) {
      throw new RuntimeException("Failed to upload file '" + object.getStorage().getFilename() + "': " + e.getMessage());
    }
  }

  public static class StreamedMediaFile extends StreamedFile {
    private final Headers originalHeaders;

    public StreamedMediaFile(GetObjectResponse file) {
      super(file, getContentType(file));
      this.originalHeaders = file.headers();
    }

    private static MediaType getContentType(GetObjectResponse file) {
      String type = file.headers().get(HttpHeaders.CONTENT_TYPE);
      return type != null ? new MediaType(type) : MediaType.APPLICATION_OCTET_STREAM_TYPE;
    }

    @Override
    public void process(MutableHttpResponse<?> response) {
      super.process(response);
      response.header(HttpHeaders.LAST_MODIFIED, originalHeaders.get(HttpHeaders.LAST_MODIFIED));
      response.header(HttpHeaders.ETAG, originalHeaders.get(HttpHeaders.ETAG));
    }
  }
}
