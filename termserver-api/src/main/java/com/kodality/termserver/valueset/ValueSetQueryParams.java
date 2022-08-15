package com.kodality.termserver.valueset;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetQueryParams extends QueryParams {
  private String id;
  private String idContains;
  private List<String> permittedIds;
  private String uri;
  private String uriContains;
  private String name;
  private String nameContains;
  private String description;
  private String descriptionContains;

  private String text;
  private String textContains;

  private Long versionId;

  private boolean decorated;

  private String lang;
  public interface Ordering {
    String id = "id";
    String uri = "uri";
    String name = "name";
    String description = "description";
  }
}
