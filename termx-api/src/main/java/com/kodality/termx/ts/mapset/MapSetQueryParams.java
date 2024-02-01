package com.kodality.termx.ts.mapset;

import com.kodality.commons.model.QueryParams;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetQueryParams extends QueryParams {
  private List<String> permittedIds;

  private String id; // eq
  private String ids;
  private String idContains;
  private String uri; // eq
  private String uriContains;
  private String identifier; // eq

  // description
  private String description; // eq
  private String descriptionStarts;
  private String descriptionContains;
  // name
  private String name; // eq
  private String nameStarts;
  private String nameContains;
  // title
  private String title; // eq
  private String titleStarts;
  private String titleContains;
  // publisher
  private String publisher; // eq
  private String publisherStarts;
  private String publisherContains;
  // text
  private String text; // eq, any(ms.id, ms.uri, ms.name) OR ms.title
  private String textContains;

  private String versionVersion; // eq
  private String versionStatus; // eq
  private String versionConceptSourceCode; // eq
  private String versionConceptTargetCode; // eq
  private LocalDate versionReleaseDate;
  private LocalDate versionReleaseDateGe;
  private boolean versionsDecorated;

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
