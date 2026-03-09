package org.termx.wiki.page;

import org.termx.wiki.tag.Tag;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageTag {
  private Long id;
  private Tag tag;
}
