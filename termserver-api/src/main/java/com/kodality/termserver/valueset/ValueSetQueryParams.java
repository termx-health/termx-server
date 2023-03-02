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
  private String ids;
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
  private String versionVersion;
  private String versionStatus;
  private String versionSource;

  private boolean decorated;

  private String codeSystem;
  private String codeSystemUri;
  private String conceptCode;

  private String lang;

  private Long projectId;
  private Long packageId;
  private Long packageVersionId;

  public interface Ordering {
    String id = "id";
    String uri = "uri";
    String name = "name";
    String description = "description";
  }
}
