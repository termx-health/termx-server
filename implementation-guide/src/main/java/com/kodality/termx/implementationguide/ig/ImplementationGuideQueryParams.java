package com.kodality.termx.implementationguide.ig;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ImplementationGuideQueryParams extends QueryParams {
  private String ids;
  private String permittedIds;
  private String uris;
  private String publisher;
  private String textContains;


  public interface Ordering {
    String id = "id";
    String uri = "uri";
    String name = "name";
  }
}
