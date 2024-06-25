package com.kodality.termx.bob;


import com.kodality.commons.model.QueryResult;
import com.kodality.termx.bob.minio.MinioService;
import io.micronaut.http.server.types.files.StreamedFile;
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


  @Transactional
  public String store(BobObject object, byte[] content) {
    Long id = objectRepository.create(object);
    object.setId(id);
    persistContent(object, content);
    return objectRepository.getUuid(id);
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
