package org.termx.taskforge.project;

import org.termx.core.acl.AclAccess;
import org.termx.core.acl.AclService;
import org.termx.taskforge.ApiError;
import org.termx.taskforge.auth.TaskforgeSessionProvider;
import org.termx.taskforge.workflow.Workflow;
import org.termx.taskforge.workflow.WorkflowService;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ProjectService {
  private final ProjectRepository projectRepository;
  private final WorkflowService workflowService;
  private final AclService aclService;
  private final TaskforgeSessionProvider session;

  public Project save(Project project) {
    aclService.validate(project.getId(), session.requireTenant(), AclAccess.edit);
    if (project.getId() == null) {
      project.setInstitution(session.requireTenant());
    } else {
      Project current = load(project.getId());
      project.setInstitution(current.getInstitution());
    }
    Long id = projectRepository.save(project);
    aclService.init(id, session.requireTenant());
    workflowService.save(id, project.getWorkflows());
    return load(id);
  }

  public Project load(Long id) {
    return projectRepository.load(id, session.requireTenant());
  }

  public Project load(String code, String institution) {
    return projectRepository.load(code, institution, session.requireTenant());
  }

  public List<Project> loadAll() {
    return projectRepository.loadAll(session.requireTenant());
  }

  public void validateTransition(Long workflowId, String from, String to) {
    if (Objects.equals(from, to)) {
      return;
    }
    Workflow wf = workflowService.load(workflowId);
    Map<String, List<String>> transitions = new HashMap<>();
    wf.getTransitions().forEach(t -> transitions.computeIfAbsent(t.getFrom(), x -> new ArrayList<>()).add(t.getTo()));
    if (!transitions.containsKey(from) || !transitions.get(from).contains(to)) {
      throw ApiError.TF101.toApiException("from", from, "to", to);
    }
  }
}
