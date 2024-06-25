package com.kodality.termx.wiki.page;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class PageComment {
  private Long id;
  @NotNull
  private Long pageContentId;
  private Long parentId;
  private String text;
  @NotBlank
  private String comment;
  private String status;
  private int replies;
  private PageCommentOptions options;

  private OffsetDateTime createdAt;
  private String createdBy;
  private OffsetDateTime modifiedAt;
  private String modifiedBy;

  @Getter
  @Setter
  public static class PageCommentOptions {
    private Integer lineNumber;
  }
}
