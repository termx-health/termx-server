package com.kodality.commons.permcache;

import com.kodality.commons.util.JsonUtil;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CacheResource {
  private Long id;
  private String resourceId;
  private String resourecType;
  private Map<String, Object> content;
  private OffsetDateTime lastRefreshed;

  public <T> T as(Class<T> clazz) {
    return JsonUtil.getObjectMapper().convertValue(content, clazz);
  }
}
