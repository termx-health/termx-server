package com.kodality.termserver.sys.lorque;

import java.nio.charset.Charset;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ProcessResult {

  private byte[] content;
  private String contentType;

  public static ProcessResult text(String result) {
    Objects.requireNonNull(result);
    return new ProcessResult().setContent(result.getBytes(Charset.defaultCharset())).setContentType(ProcessResultType.text);
  }

  public static ProcessResult binary(byte[] result) {
    Objects.requireNonNull(result);
    return new ProcessResult().setContent(result).setContentType(ProcessResultType.binary);
  }
}
