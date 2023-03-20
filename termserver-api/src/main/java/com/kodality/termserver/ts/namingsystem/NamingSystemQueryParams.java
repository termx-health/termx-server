package com.kodality.termserver.ts.namingsystem;

import com.kodality.commons.model.QueryParams;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NamingSystemQueryParams extends QueryParams {
  private String id;
  private String idContains;
  private List<String> permittedIds;
  private String name;
  private String nameContains;
  private String source;
  private String sourceContains;
  private String kind;
  private String kindContains;
  private String status;
  private String statusContains;
  private String description;
  private String descriptionContains;
  private String codeSystem;
  private String lang;

  private String text;
  private String textContains;

  public interface Ordering {
    String id = "id";
    String name = "name";
    String source = "source";
    String kind = "kind";
    String status = "status";
  }
}
