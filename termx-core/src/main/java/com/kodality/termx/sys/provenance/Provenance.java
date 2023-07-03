package com.kodality.termx.sys.provenance;

import com.kodality.commons.model.Reference;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Provenance {
  private Long id;
  private Reference target;
  private OffsetDateTime date;
  private String activity;
  private Reference author;
  private List<ProvenanceContext> context;

  public Provenance() {
  }

  public Provenance(String activity, String targetType, String targetId) {
    setActivity(activity);
    setTarget(new Reference(targetType, targetId));
  }

  public Provenance addContext(String role, String type, String id) {
    if (id != null) {
      context = context == null ? new ArrayList<>() : context;
      context.add(new ProvenanceContext().setRole(role).setEntity(new Reference(type, id)));
    }
    return this;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ProvenanceContext {
    private String role;
    private Reference entity;
  }
}
