package com.kodality.termx.ts;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Topic {
  private String text;
  private List<String> tags;
}
