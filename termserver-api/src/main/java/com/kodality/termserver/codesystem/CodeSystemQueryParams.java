package com.kodality.termserver.codesystem;

import com.kodality.commons.model.QueryParams;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemQueryParams extends QueryParams {
  private String id;
  private String idContains;
  private String uri;
  private String uriContains;
  private String name;
  private String nameContains;
  private String description;
  private String descriptionContains;

  private String text;
  private String textContains;

  private String conceptCode;
  private String conceptCodeSystemVersion;
  private boolean conceptsDecorated;

  private String versionVersion;
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
