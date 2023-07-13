package com.kodality.termx.wiki.template;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class TemplateContent {
  private Long id;
  private String lang;
  private String content;
}
