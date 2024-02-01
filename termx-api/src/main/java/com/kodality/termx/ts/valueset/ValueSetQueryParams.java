package com.kodality.termx.ts.valueset;

import com.kodality.commons.model.QueryParams;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetQueryParams extends QueryParams {
  private List<String> permittedIds;

  private String id; // eq
  private String idContains;
  private String ids; // eq, multiple
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
  // publisher
  private String publisher; // eq
  private String publisherStarts;
  private String publisherContains;
  // title
  private String title; // eq
  private String titleStarts;
  private String titleContains;
  // text
  private String text; // eq, any(vs.id, vs.uri) OR vs.title
  private String textContains;

  private Long versionId; // eq
  private String versionVersion; // eq
  private String versionStatus; // eq
  private String versionSource; // eq
  private LocalDate versionReleaseDate;
  private LocalDate versionReleaseDateGe;

  private String codeSystem; // eq
  private String codeSystemUri; // eq

  private String conceptCode; // eq
  private Long conceptId; // eq

  private boolean decorated;
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
