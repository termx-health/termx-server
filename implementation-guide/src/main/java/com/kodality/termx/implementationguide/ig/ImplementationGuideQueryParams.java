package com.kodality.termx.implementationguide.ig;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ImplementationGuideQueryParams extends QueryParams {
  private String ids;
  private String uris;
  private String publisher;
  private String textContains;

  private List<String> permittedIds;


  public interface Ordering {
    String id = "id";
    String uri = "uri";
    String name = "name";
  }
}
