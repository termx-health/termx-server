package com.kodality.termserver.orphanet.utils;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrphanetClassificationList {

  @JacksonXmlProperty(localName = "count")
  private Integer count;

  @JacksonXmlProperty(localName = "Classification")
  private List<Classification> classifications;

  @Getter
  @Setter
  public static class Classification {

    @JacksonXmlProperty
    private Long id;

    @JacksonXmlProperty(localName = "OrphaNumber")
    private String orphaNumber;

    @JacksonXmlProperty(localName = "Name")
    private Name name;

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
    private Disorder disorder;

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

  @Getter
  @Setter
  public static class Disorder {

    @JacksonXmlProperty
    private Long id;

    @JacksonXmlProperty(localName = "OrphaCode")
    private String orphaCode;

    @JacksonXmlProperty(localName = "Name")
    private Name name;

    @JacksonXmlProperty(localName = "DisorderType")
    private DisorderType disorderType;
  }

  @Getter
  @Setter
  public static class DisorderType {
    @JacksonXmlProperty(localName = "Name")
    private Name category;
  }

  @Getter
  @Setter
  public static class Name {

    @JacksonXmlProperty
    private String lang;

    @JacksonXmlText
    private String value;
  }

}
