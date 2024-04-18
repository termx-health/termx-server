package com.kodality.termx.terminology;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import java.util.Map;
import lombok.Getter;

public enum ApiError {
  TE101("TE101", "Could not create not draft version."),
  TE102("TE102", "Version '{{version}}' already exists."),
  TE103("TE103", "Can't activate version, active version '{{version}}' has overlapping periods."),
  TE104("TE104", "Version '{{version}}' is already created and final, can not be changed."),
  TE105("TE105", "Version with id '{{version}}' doesn't exist."),
  TE106("TE106", "Url not provided."),
  TE107("TE107", "Wrong resource type."),
  TE108("TE108", "Code system '{{codeSystem}}' has no draft versions."),
  TE109("TE109", "Version does not exist."),
  TE110("TE110", "Code system '{{cs}}' does not exist."),
  TE111("TE111", "Value set '{{vs}}' does not exist."),
  TE112("TE112", "Package version not found for space '{{space}}' and package '{{package}}'."),
  TE113("TE113", "Id is not allowed to contain '{{symbols}}'"),
  TE114("TE114", "Concept has non draft version, can not be deleted."),
  TE115("TE115", "Concept is linked to non draft code system version, can not be deleted."),
  TE116("TE116", "Code system '{{codeSystem}}' is not valid. Please check the data you've submitted and ensure that it adheres to the required format."),
  TE117("TE117", "Value set '{{valueSet}}' is not valid. Please check the data you've submitted and ensure that it adheres to the required format."),
  TE118("TE118", "Map set '{{mapSet}}' is not valid. Please check the data you've submitted and ensure that it adheres to the required format."),
  TE201("TE201", "Code system '{{codeSystem}}' not found."),
  TE202("TE202", "Version '{{version}}' of code system '{{codeSystem}}' doesn't exist."),
  TE203("TE203", "Property '{{propertyName}}' is used, can't be deleted."),
  TE204("TE204", "Code system is required, can't be deleted."),
  TE205("TE205", "Code system is base for code system '{{codeSystem}}', can't be deleted."),
  TE206("TE206", "Code system is used in value set, can't be deleted."),
  TE207("TE207", "Code system version is used in value set, can't be deleted."),
  TE208("TE208", "Concept version is used in map set '{{mapSet}}', can't be deleted."),
  TE210("TE210", "Concept version \"{{code}}\" must reference the codeSystem"),
  TE211("TE211", "Concept version's code is NULL"),
  TE212("TE212", "Required entity property \"{{prop}}\" is missing value(s)"),
  TE213("TE213", "Unknown entity property: {{prop}}"),
  TE214("TE214", "Value \"{{value}}\" does not match data type \"{{types}}\""),
  TE215("TE215", "Coding \"{{code}}\" is missing the \"codeSystem\" field"),
  TE216("TE216", "Coding \"{{code}}\" is missing the \"codeSystem\" field"),
  TE217("TE217", "Unknown reference \"{{code}}\" to \"{{codeSystem}}\""),
  TE218("TE218", "Property '{{propertyName}}' is used, type can't be changed."),
  TE219("TE219", "Concept '{{code}}' status and retirementDate are not correspondent. Status is active and retirementDate is specified."),
  TE301("TE301", "Version '{{version}}' of value set '{{valueSet}}' doesn't exist."),
  TE302("TE302", "Value set '{{valueSet}}' doesn't have active version."),
  TE303("TE303", "Value set is required, can't be deleted."),
  TE304("TE304", "Value set is used in map set '{{mapSet}}', can't be deleted."),
  TE305("TE305", "Value set is used in other value set rule, can't be deleted."),
  TE306("TE306", "Value set version is used in other value set rule, can't be deleted."),
  TE307("TE307", "Value set expansion failed."),
  TE308("TE308", "Rule concept line does not contain concept code information."),
  TE309("TE309", "Rule concepts contain duplicates: {{duplicates}}."),
  TE401("TE401", "Version '{{version}}' of map set '{{mapSet}}' doesn't exist."),
  TE501("TE501", "Naming system '{{namingSystem}}' not found."),
  TE601("TE601", "Cannot convert units of different kinds."),
  TE602("TE602", "Could not find common base unit."),
  TE700("TE700", "Import failed"),
  TE702("TE702", "Property '{{code}}' is missing."),
  TE703("TE703", "Property does not exist."),
  TE704("TE704", "The version of code system is final and can not be changed."),
  TE705("TE705", "The version of value set is final and can not be changed."),
  TE706("TE706", "Property type of property {{propertyName}} is not provided"),
  TE707("TE707", "The 'concept-code' and 'hierarchical-concept' can not be used simultaneously. Please specify only one of them"),
  TE708("TE708", "CSV file is missing required headers: {{headers}}"),
  TE709("TE709", "Concept code not found in provided CodeSystem. (Row nr {{rowNumber}})"),
  TE710("TE710", "Concept code not found in provided ValueSet. (Row nr {{rowNumber}})"),
  TE711("TE711", "File loading by link failed."),
  TE712("TE712", "Mapped column '{{column}}' is not present in the file header."),
  TE713("TE713", "Property \"{{prop}}\" is missing value on row(s): {{ranges}}"),
  TE714("TE714", "Several concepts match the \"{{value}}\" value"),
  TE715("TE715", "Unknown reference \"{{code}}\" to \"{{codeSystem}}\""),
  TE716("TE716", "Exception during CodeSystem import: {{exception}}"),
  TE717("TE717", "Source concept with code \"{{code}}\" is missing in CS version \"{{version}}\""),
  TE718("TE718", "Target concept with code \"{{code}}\" is missing in CS version \"{{version}}\""),
  TE719("TE719", "Constraint validation: {{error}}"),
  TE720("TE720", "Unsupported import file format: {{format}}"),
  TE721("TE721", "At least one property with type \"designation\" should be present"),
  TE722("TE722", "At least 'concept-code' or 'hierarchical-concept' should be specified"),
  TE723("TE723", "The property name and/or type is not specified for one or more columns selected for the import."),
  TE724("TE724", "Concept status '{{status}}' not supported."),
  TE725("TE725", "Exception during ValueSet import: {{exception}}"),
  TE726("TE726", "If concepts provided, then code system should be specified."),
  TE727("TE727", "Code system with '{{uri}}' not found."),
  TE728("TE728", "Language should be specified if property type is \"designation\"."),
  TE729("TE729", "The \"{{property}}\" is missing!"),
  TE731("TE731", "The \"{{property}}\" is missing! Cannot set it automatically, because cannot use ValueSet as a fallback!"),
  TE732("TE732", "The \"{{property}}\" is missing! Cannot fallback on URI as it is missing!"),
  TE733("TE733", "The \"{{property}}\" is missing! Cannot set it automatically, because too many/too few candidates!"),
  TE734("TE734", "The \"{{property}}\" is missing! Unknown source type!"),
  TE735("TE735", "The \"{{property}}\" is missing! Unknown target type!"),
  TE736("TE736", "The \"{{property}}\" is missing! Invalid source type cannot fallback! Change the source type to 'code-system' or provide values!"),
  TE737("TE737", "The \"{{property}}\" is missing! Invalid target type cannot fallback! Change the target type to 'code-system' or provide values!"),
  TE801("TE801", "Association type is required, can't be deleted."),
  TE802("TE802", "Association type is used in code system '{{codeSystem}}' association, can't be deleted."),
  TE803("TE803", "Association type is used in map set '{{mapSet}}' association, can't be deleted."),
  TE804("TE804", "Target concept is not defined."),
  TE805("TE805", "Target concept and source concepts are the same."),
  TE806("TE806", "FHIR TO FSH converter not provided"),
  TE807("TE807", "'{{format}}' is not supported.");
  @Getter
  private String code;
  @Getter
  private String message;

  ApiError(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public ApiException toApiException() {
    return new ApiClientException(code, message);
  }

  public ApiException toApiException(Map<String, Object> params) {
    return new ApiClientException(toIssue(params));
  }

  public Issue toIssue() {
    return toIssue(Map.of());
  }

  public Issue toIssue(Map<String, Object> params) {
    return Issue.error(code, message).setParams(params);
  }
}
