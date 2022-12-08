package com.kodality.termserver.codesystem;

import com.kodality.commons.model.QueryParams;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemQueryParams extends QueryParams {
  private String id;
  private String idContains;
  private List<String> permittedIds;
  private String uri;
  private String uriContains;
  private String content;
  private String name;
  private String nameContains;
  private String description;
  private String descriptionContains;
  private String baseCodeSystem;

  private String text;
  private String textContains;

  private String conceptCode;
  private String conceptCodeSystemVersion;
  private boolean conceptsDecorated;

  private Long versionId;
  private String versionVersion;
  private String versionStatus;
  private String versionSource;
  private LocalDate versionReleaseDateGe;
  private LocalDate versionExpirationDateLe;
  private boolean versionsDecorated;

  private boolean propertiesDecorated;

  private Long codeSystemEntityVersionId;

  private String lang;
  public interface Ordering {
    String id = "id";
    String uri = "uri";
    String name = "name";
    String description = "description";
  }
}
