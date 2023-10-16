package com.kodality.termx.editionint.orphanet.utils;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrphanetDisorder {
  @JacksonXmlProperty
  private String id;

  @JacksonXmlProperty(localName = "OrphaCode")
  private String orphaCode;

  @JacksonXmlProperty(localName = "ExpertLink")
  private OrphanetName expertLink;

  @JacksonXmlProperty(localName = "Name")
  private OrphanetName name;

  @JacksonXmlProperty(localName = "DisorderType")
  private DisorderType disorderType;

  @JacksonXmlProperty(localName = "SynonymList")
  private SynonymList synonymList;

  @JacksonXmlProperty(localName = "SummaryInformationList")
  private SummaryInformationList summaryInformationList;

  @JacksonXmlProperty(localName = "DisorderDisorderAssociationList")
  private DisorderDisorderAssociationList disorderDisorderAssociationList;


  // ------- Type -------
  @Getter
  @Setter
  public static class DisorderType {
    @JacksonXmlProperty(localName = "Name")
    private OrphanetName category;
  }


  // ------- Synonym -------
  @Getter
  @Setter
  public static class SynonymList {

    @JacksonXmlProperty(localName = "Synonym")
    private List<OrphanetName> synonyms;
  }


  // ------- Summary information -------
  @Getter
  @Setter
  public static class SummaryInformationList {

    @JacksonXmlProperty(localName = "SummaryInformation")
    private List<SummaryInformation> summaryInformations;
  }

  @Getter
  @Setter
  public static class SummaryInformation {

    @JacksonXmlProperty(localName = "TextSectionList")
    private TextSectionList textSectionList;
  }

  @Getter
  @Setter
  public static class TextSectionList {

    @JacksonXmlProperty(localName = "TextSection")
    private List<TextSection> textSections;
  }

  @Getter
  @Setter
  public static class TextSection {

    @JacksonXmlProperty
    private String lang;

    @JacksonXmlProperty(localName = "Contents")
    private String contents;
  }


  // ------- Association -------
  @Getter
  @Setter
  public static class DisorderDisorderAssociationList {

    @JacksonXmlProperty(localName = "DisorderDisorderAssociation")
    private List<DisorderDisorderAssociation> disorderDisorderAssociations;
  }

  @Getter
  @Setter
  public static class DisorderDisorderAssociation {

    @JacksonXmlProperty(localName = "RootDisorder")
    private RootDisorder rootDisorder;

    @JacksonXmlProperty(localName = "TargetDisorder")
    private TargetDisorder targetDisorder;

    @JacksonXmlProperty(localName = "DisorderDisorderAssociationType")
    private DisorderDisorderAssociationType associationType;
  }

  @Getter
  @Setter
  public static class TargetDisorder {

    @JacksonXmlProperty
    private String id;
  }

  @Getter
  @Setter
  public static class RootDisorder {

    @JacksonXmlProperty
    private String id;
  }

  @Getter
  @Setter
  public static class DisorderDisorderAssociationType {

    @JacksonXmlProperty
    private String id;

    @JacksonXmlProperty(localName = "Name")
    private OrphanetName name;
  }

}
