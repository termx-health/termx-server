package org.termx.bob.minio;

import org.termx.bob.BobObject;
import org.termx.bob.BobStorage;
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
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import jakarta.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
    store(object, new ByteArrayInputStream(content), content.length);
  }

  /**
   * Streaming upload: the bytes flow Netty/HTTP → temp file → this method → Minio without ever
   * being held entirely in JVM heap. Use this overload for large archives (SNOMED RF2, LOINC).
   */
  public void store(BobObject object, InputStream content, long contentLength) {
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
          uploadFile(object, content, contentLength);
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

  /**
   * Returns a raw {@link InputStream} for the object's content — for server-side consumers
   * (re-uploading to Snowstorm, spooling to a temp file) that don't want the HTTP-response
   * wrapping from {@link #retrieve(BobObject)}. The caller is responsible for closing it.
   */
  public InputStream retrieveStream(BobObject object) {
    BobStorage storage = object.getStorage();
    try {
      return minioClient.getObject(
          GetObjectArgs.builder()
              .bucket(storage.getContainer())
              .object(storage.getFullPath())
              .build());
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

  private void uploadFile(BobObject object, InputStream content, long contentLength) {
    try {
      PutObjectArgs.Builder b = PutObjectArgs.builder()
          .bucket(object.getStorage().getContainer())
          .object(object.getStorage().getFullPath())
          .contentType(object.getContentType());
      // contentLength<0 → use multipart with 5MB part size (Minio's smallest part). Lets us
      // accept truly unknown-size streams; the upper bound -1 means "until EOF".
      if (contentLength >= 0) {
        b.stream(content, contentLength, -1);
      } else {
        b.stream(content, -1, 5L * 1024 * 1024);
      }
      minioClient.putObject(b.build());
    } catch (Exception e) {
      throw new RuntimeException("Failed to upload file '" + object.getStorage().getFilename() + "': " + e.getMessage());
    }
  }

  public void delete(BobObject object) {
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder()
              .bucket(object.getStorage().getContainer())
              .object(object.getStorage().getFullPath())
              .build());
    } catch (Exception e) {
      throw new RuntimeException("Failed to delete file '" + object.getStorage().getFilename() + "': " + e.getMessage());
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
