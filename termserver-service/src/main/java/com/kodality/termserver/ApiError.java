package com.kodality.termserver;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import java.util.Map;
import lombok.Getter;

public enum ApiError {

  TE101("TE101", "Could not create not draft version."),
  TE102("TE102", "Draft version '{{version}}' already exists."),
  TE103("TE103", "Can't activate version, active version '{{version}}' has overlapping periods."),
  TE104("TE104", "Version '{{version}}' is already created and final, can not be changed."),
  TE105("TE105", "Version with id '{{version}}' doesn't exist."),
  TE106("TE106", "Url not provided."),
  TE107("TE107", "Wrong resource type."),
  TE108("TE108", "Code system '{{codeSystem}}' has no draft versions."),

  TE201("TE201", "Code system '{{codeSystem}}' not found."),
  TE202("TE202", "Version '{{version}}' of code system '{{codeSystem}}' doesn't exist."),

  TE301("TE301", "Version '{{version}}' of value set '{{valueSet}}' doesn't exist."),
  TE302("TE302", "Value set '{{valueSet}}' doesn't have active version."),

  TE401("TE401", "Version '{{version}}' of map set '{{mapSet}}' doesn't exist."),

  TE501("TE501", "Naming system '{{namingSystem}}' not found."),

  TE601("TE601", "Cannot convert units of different kinds."),
  TE602("TE602", "Could not find common base unit."),

  TE700("TE700", "Import failed"),
  TE701("TE701", "File has invalid encoding. UTF-8 is required."),
  TE702("TE702", "Property 'concept-code' not provided."),
  TE703("TE703", "Property does not exist."),
  TE704("TE704", "The version of code system is final and can not be changed."),
  TE705("TE705", "The version of value set is final and can not be changed."),
  TE706("TE706", "Property type of property {{propertyName}} is not provided"),
  TE707("TE707", "Multiple preferred concept-code properties are selected. Please select one!"),
  TE708("TE708", "CSV file is missing required headers: {{headers}}"),
  TE709("TE709", "Concept code not found in provided CodeSystem. (Row nr {{rowNumber}})"),
  TE710("TE710", "Concept code not found in provided ValueSet. (Row nr {{rowNumber}})"),
  ;


  @Getter
  private String code;
  @Getter
  private String message;

  ApiError(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public ApiException toApiException() {
    ApiClientException exception = new ApiClientException(code, message);
    exception.getIssues().forEach(issue -> issue.setParams(Map.of())); //TODO fix in commons
    return exception;
  }

  public ApiException toApiException(Map<String, Object> params) {
    return new ApiClientException(toIssue(params));
  }

  public Issue toIssue(Map<String, Object> params) {
    return Issue.error(code, message).setParams(params);
  }

}
