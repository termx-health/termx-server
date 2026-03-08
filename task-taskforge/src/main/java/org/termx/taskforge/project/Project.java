package org.termx.taskforge.project;

import com.kodality.commons.model.LocalizedName;
import org.termx.taskforge.workflow.Workflow;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Project {
  private Long id;
  private String institution;
  private String code;
  private LocalizedName names;
  private List<Workflow> workflows;
}
