package com.kodality.termx.modeler.fhir;

import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.rest.filter.KefhirRequestFilter;
import com.kodality.kefhir.rest.filter.KefhirResponseFilter;
import com.kodality.kefhir.rest.model.KefhirRequest;
import com.kodality.kefhir.rest.model.KefhirResponse;
import com.kodality.kefhir.structure.service.ResourceFormatService;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.other.Binary;
import io.micronaut.http.MediaType;
import jakarta.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r5.model.ResourceType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * accepting custom resources caused too much trouble, so for now:
 * this converts incoming operation $transform resources to binary, and operation then converts back from binary.
 * resulting Parameters is converted to json, but the input resource type remains (in binary, same as passed)
 */
@Singleton
@RequiredArgsConstructor
public class StructureMapTransformOperationHack implements KefhirRequestFilter, KefhirResponseFilter {
  private final ResourceFormatService resourceFormatService;

  @Override
  public Integer getOrder() {
    return 40;
  }

  @Override
  public void handleRequest(KefhirRequest request) {
    if (!request.getPath().contains("$transform")) {
      return;
    }
    String format = resourceFormatService.findPresenter(request.getContentTypeName()).orElseThrow().getName();
    String resourceType = readResourceType(format, request.getBody());

    if (resourceType.equals(ResourceType.Parameters.name())) {
      if (format.equals("json")) {
        Map<String, Object> parameters = JsonUtil.toMap(request.getBody());
        ((List<Map<String, Object>>) parameters.get("parameter")).stream().filter(p -> p.containsKey("resource")).forEach(p -> {
          Binary binary = new Binary();
          binary.setDataString(JsonUtil.toJson(p.get("resource")));
          p.put("resource", binary);
        });
        request.setBody(FhirMapper.toJson(parameters)).setContentType(MediaType.APPLICATION_JSON_TYPE);
        return;
      }
      if (format.equals("xml")) {
        //TODO
      }
      return;
    }

    request.getProperties().put("original-content-type", request.getContentType());
    Binary binary = new Binary();
    binary.setDataString(request.getBody());
    request.setBody(FhirMapper.toJson(binary)).setContentType(MediaType.APPLICATION_JSON_TYPE);
  }


  private String readResourceType(String format, String content) {
    if (format.equals("xml")) {
      try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(content.getBytes()));
        Element root = document.getDocumentElement();
        return root.getTagName();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    if (format.equals("json")) {
      return (String) JsonUtil.toMap(content).get("resourceType");
    }
    throw new RuntimeException(format + " is unknown");
  }

  @Override
  public void handleResponse(KefhirResponse response, KefhirRequest request) {
    if (request.getProperties().containsKey("original-content-type")) {
      request.setContentType((MediaType) request.getProperties().get("original-content-type"));
    }
  }
}
