package com.kodality.termserver.thesaurus.template;

import com.kodality.commons.model.LocalizedName;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Template {
  private Long id;
  private String code;
  private LocalizedName names;
  private String contentType;
  private List<TemplateContent> contents;
}
