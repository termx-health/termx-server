package org.termx.taskforge.api;

import com.kodality.commons.micronaut.BeanContext;
import org.termx.taskforge.user.TaskforgeUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class TaskforgeUserProvider {
  protected static String select = "jsonb_build_object('sub', %s)";

  public static String select(String ref) {
    return String.format(select, ref);
  }

  public String getReferenceId(TaskforgeUser u) {
    return u == null ? null : u.getSub();
  }

  public static TaskforgeUser ofReference(ResultSet rs, int i, Class<?> x) {
    try {
      String refId = rs.getString(i);
      return refId == null ? null : BeanContext.getBean(TaskforgeUserProvider.class).getByReferenceId(refId);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public TaskforgeUser getByReferenceId(String referenceId) {
    return referenceId == null ? null : new TaskforgeUser().setSub(referenceId);
  }

  public List<TaskforgeUser> getUsers() {
    return new ArrayList<>();
  }
}
