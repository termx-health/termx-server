package org.termx.bob;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ForbiddenException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.Issue;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionInfo;
import org.termx.core.auth.SessionStore;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.types.files.StreamedFile;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Generic REST surface for the Binary Object Bank ({@code bob.object}).
 *
 * <p>Every endpoint dispatches to a per-container {@link BobContainerAuthorizer}, looked up
 * by the object's (or query's) container value. Containers without a registered authorizer
 * are inaccessible — a new bucket only becomes reachable once its owning module declares
 * a {@link BobContainerAuthorizer} bean. The {@link Authorized} annotation is intentionally
 * left empty so the {@link org.termx.core.auth.AuthorizationFilter} allows authenticated
 * users past the static check, and the dynamic per-container authz runs inside each handler.</p>
 */
@Singleton
@Controller("/bob/objects")
public class BobObjectController {
  private final BobObjectService objectService;
  private final Map<String, BobContainerAuthorizer> authorizers;

  public BobObjectController(BobObjectService objectService, List<BobContainerAuthorizer> authorizers) {
    this.objectService = objectService;
    this.authorizers = authorizers.stream().collect(Collectors.toMap(BobContainerAuthorizer::getContainer, a -> a));
  }

  @Authorized
  @Get("{?params*}")
  public QueryResult<BobObject> query(BobObjectQueryParams params) {
    if (params.getContainer() == null || params.getContainer().isBlank()) {
      throw new ApiClientException(Issue.error("BOB001", "container query parameter is required"));
    }
    BobContainerAuthorizer authorizer = requireAuthorizer(params.getContainer());
    authorizer.checkRead(session(), null);
    return objectService.query(params);
  }

  @Authorized
  @Get("/{uuid}")
  public BobObject load(@PathVariable String uuid) {
    BobObject object = requireExisting(uuid);
    requireAuthorizer(object.getStorage().getContainer()).checkRead(session(), object);
    return object;
  }

  @Authorized
  @Get(value = "/{uuid}/content", produces = MediaType.APPLICATION_OCTET_STREAM)
  public StreamedFile loadContent(@PathVariable String uuid) {
    BobObject object = requireExisting(uuid);
    requireAuthorizer(object.getStorage().getContainer()).checkRead(session(), object);
    return objectService.loadContent(object);
  }

  /**
   * Multipart upload. The {@code file} part is streamed to a temp file on disk and then to Minio,
   * so the JVM heap holds at most a small buffer regardless of file size. Other parts ({@code
   * container}, {@code meta}, {@code description}, {@code path}, {@code contentType}) are
   * received as normal form fields.
   */
  @Authorized
  @Post(consumes = MediaType.MULTIPART_FORM_DATA)
  public Mono<BobObject> create(
      StreamingFileUpload file,
      @Part("container") String container,
      @Nullable @Part("meta") String meta,
      @Nullable @Part("description") String description,
      @Nullable @Part("path") String path,
      @Nullable @Part("contentType") String contentType
  ) {
    if (container == null || container.isBlank()) {
      throw new ApiClientException(Issue.error("BOB001", "container query parameter is required"));
    }
    BobObject draft = new BobObject()
        .setContentType(contentType != null ? contentType : (file.getContentType().isPresent() ? file.getContentType().get().getName() : null))
        .setMeta(parseJsonMap(meta))
        .setDescription(description)
        .setStorage(new BobStorage()
            .setContainer(container)
            .setPath(path != null ? path : "/")
            .setFilename(file.getFilename()));
    requireAuthorizer(container).checkWrite(session(), draft);

    // Capture the session up-front; the streaming completion runs on a Netty event loop with
    // no servlet thread-local, so SessionStore.require() inside the lambda would fail.
    SessionInfo capturedSession = session();

    Path temp;
    try {
      temp = Files.createTempFile("bob-", ".upload");
    } catch (IOException e) {
      throw new RuntimeException("Failed to create temp file for upload", e);
    }

    Publisher<Boolean> transfer = file.transferTo(temp.toFile());
    return Mono.from(transfer).map(ok -> {
      if (!Boolean.TRUE.equals(ok)) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {}
        throw new RuntimeException("Failed to spool upload to disk");
      }
      try {
        SessionStore.setLocal(capturedSession);
        String uuid = objectService.store(draft, temp);
        return objectService.load(uuid);
      } finally {
        SessionStore.clearLocal();
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {}
      }
    });
  }

  @Authorized
  @Patch("/{uuid}")
  public BobObject update(@PathVariable String uuid, @Body BobObjectPatch patch) {
    BobObject existing = requireExisting(uuid);
    BobContainerAuthorizer authorizer = requireAuthorizer(existing.getStorage().getContainer());
    authorizer.checkWrite(session(), existing);
    BobObject patchObj = new BobObject()
        .setMeta(patch.getMeta())
        .setDescription(patch.getDescription());
    return objectService.update(uuid, patchObj);
  }

  @Authorized
  @Delete("/{uuid}")
  public void delete(@PathVariable String uuid) {
    BobObject existing = requireExisting(uuid);
    requireAuthorizer(existing.getStorage().getContainer()).checkWrite(session(), existing);
    objectService.delete(uuid);
  }

  private BobObject requireExisting(String uuid) {
    BobObject object = objectService.load(uuid);
    if (object == null) {
      throw new NotFoundException("BobObject '" + uuid + "' does not exist");
    }
    return object;
  }

  private BobContainerAuthorizer requireAuthorizer(String container) {
    BobContainerAuthorizer a = authorizers.get(container);
    if (a == null) {
      // No authorizer registered → treat as forbidden. We use ForbiddenException rather than
      // 404 to avoid leaking whether the container exists in storage.
      throw new ForbiddenException("forbidden");
    }
    return a;
  }

  private SessionInfo session() {
    return SessionStore.require();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseJsonMap(String json) {
    if (json == null || json.isBlank()) {
      return new HashMap<>();
    }
    try {
      return JsonUtil.fromJson(json, Map.class);
    } catch (Exception e) {
      throw new ApiClientException(Issue.error("BOB004", "meta is not valid JSON"));
    }
  }

  @Getter
  @Setter
  public static class BobObjectPatch {
    private Map<String, Object> meta;
    private String description;
  }
}
