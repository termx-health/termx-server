package com.kodality.termserver.observationdefintion;

import com.kodality.commons.model.LocalizedName;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ObservationDefinition {
  private Long id;
  private String code;
  private String version;
  private String publisher;
  private String url;
  private String status;
  private LocalizedName names;
  private LocalizedName alias;
  private LocalizedName definition;
  private List<ObservationDefinitionKeyWord> keywords;
  private String category;
  private String timePrecision;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ObservationDefinitionKeyWord {
    private String lang;
    private String word;
  }

}
