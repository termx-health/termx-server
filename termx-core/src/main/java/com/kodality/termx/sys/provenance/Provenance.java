package com.kodality.termx.sys.provenance;

import com.kodality.commons.model.Reference;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private ProvenanceDetail detail;

  public Provenance() {
  }

  public Provenance(String activity, String targetType, String targetId) {
    this(activity, targetType, targetId, null);
  }

  public Provenance(String activity, String targetType, String targetId, String targetDisplay) {
    setActivity(activity);
    setTarget(new Reference(targetType, targetId).setDisplay(targetDisplay));
  }

  public Provenance addContext(String role, String type, String id) {
    return addContext(role, type, id, null);
  }

  public Provenance addContext(String role, String type, String id, String display) {
    if (id != null) {
      context = context == null ? new ArrayList<>() : context;
      context.add(new ProvenanceContext().setRole(role).setEntity(new Reference(type, id).setDisplay(display)));
    }
    return this;
  }

  public Provenance setChanges(Map<String, ProvenanceChange> changes) {
    if (changes != null) {
      this.setDetail(this.getDetail() == null ? new ProvenanceDetail() : this.getDetail());
      this.getDetail().setChanges(changes);
    }
    return this;
  }

  public Provenance setMessages(Map<String, String> messages) {
    if (messages != null) {
      this.setDetail(this.getDetail() == null ? new ProvenanceDetail() : this.getDetail());
      this.getDetail().setMessages(messages);
    }
    return this;
  }

  public Provenance addMessage(String key, String value) {
    this.setDetail(this.getDetail() == null ? new ProvenanceDetail() : this.getDetail());
    this.getDetail().setMessages(this.getDetail().getMessages() == null ? new HashMap<>() : this.getDetail().getMessages());
    this.getDetail().getMessages().put(key, value);
    return this;
  }

  public Provenance created(boolean created) {
    if (created) {
      created();
    }
    return this;
  }

  public Provenance created() {
    this.addMessage("result", "created");
    return this;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ProvenanceContext {
    private String role;
    private Reference entity;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ProvenanceDetail {
    private Map<String, ProvenanceChange> changes;
    private Map<String, String> messages;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ProvenanceChange {
    private Object left;
    private Object right;

    public static <T> ProvenanceChange of(T left, T right) {
      return new ProvenanceChange().setLeft(left).setRight(right);
    }
  }
}
