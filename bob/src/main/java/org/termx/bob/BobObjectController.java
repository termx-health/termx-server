package org.termx.bob;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ForbiddenException;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.micronaut.rest.MultipartBodyReader;
import com.kodality.commons.micronaut.rest.MultipartBodyReader.Attachment;
import com.kodality.commons.model.Issue;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionInfo;
import org.termx.core.auth.SessionStore;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.server.types.files.StreamedFile;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

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
  @Get
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

  @Authorized
  @Post(consumes = MediaType.MULTIPART_FORM_DATA)
  public BobObject create(@Body MultipartBody partz) {
    MultipartBodyReader.MultipartBody body = MultipartBodyReader.readMultipart(partz);

    String container = require(body.getTextParts().get("container"), "container");
    Attachment file = body.getAttachments().get("file");
    if (file == null) {
      throw new ApiClientException(Issue.error("BOB002", "file part is required"));
    }
    String path = body.getTextParts().getOrDefault("path", "/");
    Map<String, Object> meta = parseJsonMap(body.getTextParts().get("meta"));
    String description = body.getTextParts().get("description");
    String contentType = body.getTextParts().getOrDefault("contentType", file.getContentType());

    BobObject draft = new BobObject()
        .setContentType(contentType)
        .setMeta(meta)
        .setDescription(description)
        .setStorage(new BobStorage()
            .setContainer(container)
            .setPath(path)
            .setFilename(file.getFileName()));
    requireAuthorizer(container).checkWrite(session(), draft);

    String uuid = objectService.store(draft, file.getContent());
    return objectService.load(uuid);
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

  private static String require(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new ApiClientException(Issue.error("BOB003", "{{name}} is required", Map.of("name", name)));
    }
    return value;
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
