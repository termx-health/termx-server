package com.kodality.termx.bob;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;


@Getter
@Setter
@Accessors(chain = true)
public class BobObject {
  private Long id;
  private String uuid;
  private String contentType;
  private Map<String, Object> meta;
  private String description;

  private BobStorage storage;
}
