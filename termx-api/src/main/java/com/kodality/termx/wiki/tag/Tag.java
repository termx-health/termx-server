package com.kodality.termx.wiki.tag;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Tag {
  private Long id;
  private String text;
}
