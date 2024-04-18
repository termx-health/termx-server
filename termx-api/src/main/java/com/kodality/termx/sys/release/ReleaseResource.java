package com.kodality.termx.sys.release;

import com.kodality.commons.model.LocalizedName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ReleaseResource {
  private Long id;
  private String resourceType;
  private String resourceId;
  private String resourceVersion;
  private LocalizedName resourceNames;
}
