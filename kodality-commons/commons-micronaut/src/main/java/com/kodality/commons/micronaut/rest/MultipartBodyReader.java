package com.kodality.commons.micronaut.rest;

import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.netty.multipart.NettyCompletedAttribute;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import reactor.core.publisher.Flux;

public class MultipartBodyReader {

  public static MultipartBody readMultipart(io.micronaut.http.server.multipart.MultipartBody partz) {
    MultipartBody result = new MultipartBody();
    Flux.from(partz).flatMap(part -> {
      try {
        if (part instanceof CompletedFileUpload x) {
          Attachment a = new Attachment();
          a.setContentType(x.getContentType().map(MediaType::getName).orElse(null));
          a.setContentLength(x.getSize());
          a.setFileName(x.getFilename());
          a.setContent(x.getBytes());
          result.getAttachments().put(x.getName(), a);
        }
        if (part instanceof NettyCompletedAttribute) {
          result.getTextParts().put(part.getName(), new String(part.getBytes()));
        }
      } catch (Exception e) {
        return Flux.error(e);
      }
      return Flux.just(part);
    }).collectList().block();
    return result;
  }

  @Getter
  @Setter
  public static class MultipartBody {
    private Map<String, Attachment> attachments = new HashMap<>();
    private Map<String, String> textParts = new HashMap<>();
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class Attachment {
    private String fileName;
    private Long contentLength;
    private String contentType;
    private byte[] content;
  }
}
