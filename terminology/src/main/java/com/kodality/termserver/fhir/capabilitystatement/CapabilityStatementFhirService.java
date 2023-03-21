package com.kodality.termserver.fhir.capabilitystatement;

import com.kodality.commons.util.JsonUtil;
import java.io.IOException;
import java.nio.charset.Charset;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;

@Singleton
@RequiredArgsConstructor
public class CapabilityStatementFhirService {

  public Object get() {
    String filePath = "/metadata/CapabilityStatement.json";
    try {
      return JsonUtil.fromJson(IOUtils.resourceToString(filePath, Charset.defaultCharset()), Object.class);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }
}
