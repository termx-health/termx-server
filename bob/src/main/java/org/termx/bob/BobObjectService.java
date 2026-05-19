package org.termx.bob;


import com.kodality.commons.model.QueryResult;
import org.termx.bob.minio.MinioService;
import io.micronaut.http.server.types.files.StreamedFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;


// BOB aka. Binary Object Bank

@Singleton
@RequiredArgsConstructor
public class BobObjectService {
  private final BobObjectRepository objectRepository;
  private final Optional<MinioService> minioService;


  public QueryResult<BobObject> query(BobObjectQueryParams params) {
    return objectRepository.query(params);
  }

  public BobObject load(String uuid) {
    return objectRepository.get(uuid);
  }

  public StreamedFile loadContent(BobObject object) {
    return getMinio().retrieve(object);
  }

  /**
   * Raw {@link InputStream} for server-side consumers (re-uploading to Snowstorm, spooling
   * to a temp file). Caller closes it.
   */
  public InputStream loadContentStream(BobObject object) {
    return getMinio().retrieveStream(object);
  }


  @Transactional
  public String store(BobObject object, byte[] content) {
    Long id = objectRepository.create(object);
    object.setId(id);
    persistContent(object, content);
    return objectRepository.getUuid(id);
  }

  /**
   * Streaming variant: bytes from {@code filePath} flow to Minio without being buffered in
   * the JVM heap. Use this for large uploads (SNOMED RF2, LOINC) — typically the controller
   * has already spooled the multipart body to a temp file on disk and just passes the path.
   */
  @Transactional
  public String store(BobObject object, Path filePath) {
    Long id = objectRepository.create(object);
    object.setId(id);
    persistContent(object, filePath);
    return objectRepository.getUuid(id);
  }

  private void persistContent(BobObject object, Path filePath) {
    BobStorage storage = object.getStorage();
    prepareTypeAndPath(storage);
    try (InputStream in = Files.newInputStream(filePath)) {
      long size = Files.size(filePath);
      getMinio().store(object, in, size);
    } catch (IOException e) {
      throw new RuntimeException("Failed to stream file '" + filePath + "' to Minio: " + e.getMessage(), e);
    }
    Long storageId = objectRepository.createStorage(object.getId(), storage);
    storage.setId(storageId);
  }

  @Transactional
  public BobObject update(String uuid, BobObject patch) {
    BobObject existing = load(uuid);
    if (existing == null) {
      throw new RuntimeException("BobObject not found: " + uuid);
    }
    BobObject merged = new BobObject()
        .setId(existing.getId())
        .setMeta(patch.getMeta() != null ? patch.getMeta() : existing.getMeta())
        .setDescription(patch.getDescription() != null ? patch.getDescription() : existing.getDescription());
    objectRepository.update(existing.getId(), merged);
    return load(uuid);
  }

  @Transactional
  public void delete(String uuid) {
    BobObject object = load(uuid);
    getMinio().delete(object);
    objectRepository.deleteStorage(object.getId());
    objectRepository.delete(object.getId());
  }

  private void persistContent(BobObject object, byte[] content) {
    BobStorage storage = object.getStorage();
    prepareTypeAndPath(storage);

    getMinio().store(object, content);
    Long storageId = objectRepository.createStorage(object.getId(), storage);

    storage.setId(storageId);
  }

  private void prepareTypeAndPath(BobStorage storage) {
    if (storage.getStorageType() == null) {
      storage.setStorageType("minio");
    }
    if (storage.getPath() == null) {
      storage.setPath("/");
    }
    if (!storage.getPath().startsWith("/")) {
      storage.setPath("/" + storage.getPath());
    }
    if (!storage.getPath().endsWith("/")) {
      storage.setPath(storage.getPath() + "/");
    }
  }

  private MinioService getMinio() {
    return minioService.orElseThrow(() -> new RuntimeException("minio is not configured"));
  }
}
