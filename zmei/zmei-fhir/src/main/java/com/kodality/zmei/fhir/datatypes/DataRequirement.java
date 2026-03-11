package com.kodality.zmei.fhir.datatypes;

import com.kodality.zmei.fhir.Element;
import java.time.OffsetDateTime;
import java.util.List;
import javax.swing.GroupLayout.Group;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class DataRequirement extends Element {
  private String type;
  private String profile;
  private CodeableConcept subjectCodeableConcept;
  private String subjectReference;
  private String mustSupport;
  private List<DataRequirementCodeFilter> codeFilter;
  private List<DataRequirementDateFilter> dateFilter;
  private List<DataRequirementValueFilter> valueFilter;
  private Integer limit;
  private List<DataRequirementSort> sort;

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DataRequirementCodeFilter extends Element {
    private String path;
    private String searchParam;
    private String valueSet;
    private List<Coding> code;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DataRequirementDateFilter extends Element {
    private String path;
    private String searchParam;
    private OffsetDateTime valueDateTime;
    private Period valuePeriod;
    private Duration valueDuration;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DataRequirementValueFilter extends Element {
    private String path;
    private String searchParam;
    private String comparator;
    private OffsetDateTime valueDateTime;
    private Period valuePeriod;
    private Duration valueDuration;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DataRequirementSort extends Element {
    private String path;
    private String direction;
  }

}
