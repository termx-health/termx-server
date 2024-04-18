package com.kodality.termx.sys.release;

import com.kodality.commons.model.LocalizedName;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Release {
  private Long id;
  private String code;
  private LocalizedName names;
  private OffsetDateTime planned;
  private OffsetDateTime releaseDate;
  private String status;
  private List<String> authors;
  private List<ReleaseResource> resources;
}
