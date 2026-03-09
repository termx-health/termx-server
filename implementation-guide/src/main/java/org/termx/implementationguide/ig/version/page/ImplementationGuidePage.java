package org.termx.implementationguide.ig.version.page;


import com.kodality.commons.model.CodeName;
import org.termx.implementationguide.ig.version.group.ImplementationGuideGroup;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ImplementationGuidePage {
  private Long id;
  private CodeName space;
  private String page;
  private String name;
  private String type;
  private ImplementationGuideGroup group;
}
