package com.kodality.termx.ts.codesystem;

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
  private List<String> permittedIds;
  private String id; // eq
  private String ids; // eq
  private String idContains;
  private String uri; // eq, multiple
  private String uriContains; //
  private String baseCodeSystem; // eq
  private String content; // eq
  private String identifier; // eq, cs.identifier OR csv.identifier

  // description
  private String description; // eq
  private String descriptionStarts;
  private String descriptionContains;
  // name
  private String name; // eq
  private String nameStarts;
  private String nameContains;
  // publisher
  private String publisher; // eq
  private String publisherStarts;
  private String publisherContains;
  // title
  private String title; // eq
  private String titleStarts;
  private String titleContains;
  //text
  private String text; // eq, any(cs.id, cs.uri, cs.name) OR cs.title
  private String textContains;
  // concept
  private String conceptCode; // eq
  private String conceptCodeSystemVersion; // decorate

  private Long versionId; // eq
  private String versionVersion; // eq
  private String versionStatus; // eq
  private LocalDate versionReleaseDate;
  private LocalDate versionReleaseDateGe;
  private LocalDate versionExpirationDateLe;
  private Long codeSystemEntityVersionId; // eq

  private boolean versionsDecorated; // decorate
  private boolean conceptsDecorated; // decorate
  private boolean propertiesDecorated;
  private String lang; // used for sort

  private Long spaceId;
  private Long packageId;
  private Long packageVersionId;

  public interface Ordering {
    String id = "id";
    String uri = "uri";
    String name = "name";
    String description = "description";
  }
}
