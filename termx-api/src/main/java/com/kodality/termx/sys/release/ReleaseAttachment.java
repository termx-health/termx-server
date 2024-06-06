package com.kodality.termx.sys.release;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ReleaseAttachment {
  private String fileId;
  private String fileName;
  private String contentType;
}
