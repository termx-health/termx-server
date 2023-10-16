package com.kodality.termx.editionint.orphanet.utils;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClassificationList {

  @JacksonXmlProperty(localName = "count")
  private Integer count;

  @JacksonXmlProperty(localName = "Classification")
  private List<Classification> classifications;

  @Getter
  @Setter
  public static class Classification {

    @JacksonXmlProperty
    private String id;

    @JacksonXmlProperty(localName = "OrphaNumber")
    private String orphaNumber;

    @JacksonXmlProperty(localName = "Name")
    private OrphanetName name;

    @JacksonXmlProperty(localName = "ClassificationNodeRootList")
    private ClassificationNodeRootList classificationNodeRootList;
  }

  @Getter
  @Setter
  public static class ClassificationNodeRootList {

    @JacksonXmlProperty
    private Long count;

    @JacksonXmlProperty(localName = "ClassificationNode")
    private List<ClassificationNode> classificationNodes;
  }

  @Getter
  @Setter
  public static class ClassificationNode {

    @JacksonXmlProperty(localName = "Disorder")
    private OrphanetDisorder disorder;

    @JacksonXmlProperty(localName = "ClassificationNodeChildList")
    private ClassificationNodeChildList classificationNodeChildList;
  }


  @Getter
  @Setter
  public static class ClassificationNodeChildList {

    @JacksonXmlProperty
    private Integer count;

    @JacksonXmlProperty(localName = "ClassificationNode")
    private List<ClassificationNode> classificationNodes;
  }
}
