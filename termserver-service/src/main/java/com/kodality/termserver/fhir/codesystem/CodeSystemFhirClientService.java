package com.kodality.termserver.fhir.codesystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class CodeSystemFhirClientService {

  private FhirClient<CodeSystem> getClient() {
    String fhirUrl = SessionStore.require().getProviderProperties().get("iss");
    fhirUrl = "https://kefhir.kodality.dev/fhir"; //FIXME
    return new FhirClient<>(fhirUrl + "/CodeSystem", CodeSystem.class, b -> b.header("Authorization", "Bearer " + SessionStore.require().getToken()));
  }

  public com.kodality.termserver.codesystem.CodeSystem load(String codeSystem) {
    return CodeSystemFhirImportMapper.mapCodeSystem(getClient().read(codeSystem).join());
  }

  public QueryResult<com.kodality.termserver.codesystem.CodeSystem> search(CodeSystemQueryParams tsParams) {
    Bundle fResult = getClient().search(mapFhirParams(tsParams)).join();
    QueryResult<com.kodality.termserver.codesystem.CodeSystem> tsResult = new QueryResult<>(fResult.getTotal(), tsParams);
    if (fResult.getEntry() != null) {
      tsResult.setData(fResult.getEntry().stream().map(entry -> CodeSystemFhirImportMapper.mapCodeSystem(entry.getResource())).collect(Collectors.toList()));
    }
    return tsResult;
  }

  private FhirQueryParams mapFhirParams(CodeSystemQueryParams tsParams) {
    FhirQueryParams fParams = new FhirQueryParams();
    fParams.putSingle(FhirQueryParams.count, tsParams.getLimit().toString());
    fParams.setOffset(tsParams.getOffset());
    return fParams;
  }

}
