package com.kodality.zmei.cds;

import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.util.Lists;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class CdsServiceResponse {
  private List<CdsResponseCard> cards;
  private List<CdsResponseAction> systemActions;

  public CdsServiceResponse(List<CdsResponseCard> cards) {
    this.cards = cards;
  }

  public CdsServiceResponse addCard(CdsResponseCard card) {
    this.cards = Lists.add(this.cards, card);
    return this;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CdsResponseCard {
    private String uuid;
    private String summary;
    private String detail;
    private String indicator;
    private CdsResponseCardSource source;
    private List<CdsResponseCardSuggestion> suggestions;
    private String selectionBehavior;
    private List<CdsResponseCardCoding> overrideReasons;
    private List<CdsResponseCardLink> links;

    public CdsResponseCard() {
    }

    public CdsResponseCard(String indicator, String summary) {
      this.summary = summary;
      this.indicator = indicator;
    }

    public CdsResponseCard addSuggestion(CdsResponseCardSuggestion suggestion) {
      this.suggestions = Lists.add(this.suggestions, suggestion);
      return this;
    }
  }

  public interface CdsResponseCardIndicator {
    String info = "info";
    String warning = "warning";
    String critical = "critical";
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CdsResponseCardSource {
    private String label;
    private String url;
    private String icon;
    private CdsResponseCardCoding topic;

    public CdsResponseCardSource() {
    }

    public CdsResponseCardSource(String label) {
      this.label = label;
    }
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CdsResponseCardSuggestion {
    private String label;
    private String uuid;
    private Boolean isRecommended;
    private List<CdsResponseAction> actions;

    public CdsResponseCardSuggestion() {
    }

    public CdsResponseCardSuggestion(String label) {
      this.label = label;
    }
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CdsResponseAction {
    private String type;
    private String description;
    /*
    A FHIR resource.
     When the type attribute is create, the resource attribute SHALL contain a new FHIR resource to be created.
     For update, this holds the updated resource in its entirety and not just the changed fields.
     Use of this field to communicate a string of a FHIR id for delete suggestions is DEPRECATED
     and resourceId SHOULD be used instead.
     */
    private Resource resource;
    /* SHOULD be provided when the type attribute is delete.*/
    private String resourceId;

    public CdsResponseAction() {
    }

    public CdsResponseAction(String type, String description) {
      this.type = type;
      this.description = description;
    }
  }

  public interface CdsResponseActionType {
    String create = "create";
    String update = "update";
    String delete = "delete";
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class CdsResponseCardLink {
    private String label;
    private String url;
    private String type;
    private String appContext;

    public CdsResponseCardLink() {
    }

    public CdsResponseCardLink(String label, String url, String type) {
      this.label = label;
      this.url = url;
      this.type = type;
    }

  }

  @Getter
  @Setter
  @Accessors(chain = true)
  private static class CdsResponseCardCoding {
    private String code;
    private String system;
    /* REQUIRED for Override Reasons provided by the CDS Service, OPTIONAL for Topic. */
    private String display;
    private List<CdsResponseCardLink> links;

    public CdsResponseCardCoding() {
    }

    public CdsResponseCardCoding(String code, String system) {
      this.code = code;
      this.system = system;
    }
  }

  public interface CdsResponseCardSelectionBehavior {
    String atMostOne = "at-most-one";
    String any = "any";
  }


//  {
//    "cards": [
//    {
//      "summary": "<140 char Summary Message",
//      "detail": "optional GitHub Markdown details",
//      "indicator": "info",
//      "source": {
//          "label": "Human-readable source label",
//          "url": "https://example.com",
//          "icon": "https://example.com/img/icon-100px.png
//          "topic": {
//              "system": "http://example.org/cds-services/fhir/CodeSystem/topics",
//              "code": "12345",
//              "display": "Mosquito born virus"
//          }
//      },
//      "suggestions": [
//        {
//          "label": "Human-readable suggestion label",
//          "uuid": "e1187895-ad57-4ff7-a1f1-ccf954b2fe46",
//          "actions": [
//             {
//               "type": "create",
//               "description": "Create a prescription for Acetaminophen 250 MG",
//               "resource": {
//                "resourceType": "MedicationRequest",
//                   "...": "<snipped for brevity>"
//                }
//              }
//           ]
//        }
//     ],
//      "links": [
//      {
//        "label": "SMART Example App",
//          "...": "<snipped for brevity>"
//      }
// ]
//    }
// ]
//  }
}
