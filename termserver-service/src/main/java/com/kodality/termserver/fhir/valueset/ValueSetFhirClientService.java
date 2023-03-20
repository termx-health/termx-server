package com.kodality.termserver.fhir.valueset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.ts.valueset.ValueSetQueryParams;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class ValueSetFhirClientService {

  private FhirClient<ValueSet> getClient() {
    String fhirUrl = SessionStore.require().getProviderProperties().get("iss");
    fhirUrl = "https://kefhir.kodality.dev/fhir"; //FIXME
    return new FhirClient<>(fhirUrl + "/ValueSet", ValueSet.class, b -> b.header("Authorization", "Bearer " + SessionStore.require().getToken()));
  }

  public com.kodality.termserver.ts.valueset.ValueSet load(String ValueSet) {
    return ValueSetFhirImportMapper.mapValueSet(getClient().read(ValueSet).join());
  }

  public QueryResult<com.kodality.termserver.ts.valueset.ValueSet> search(ValueSetQueryParams tsParams) {
    Bundle fResult = getClient().search(mapFhirParams(tsParams)).join();
    QueryResult<com.kodality.termserver.ts.valueset.ValueSet> tsResult = new QueryResult<>(fResult.getTotal(), tsParams);
    if (fResult.getEntry() != null) {
      tsResult.setData(fResult.getEntry().stream().map(entry -> ValueSetFhirImportMapper.mapValueSet(entry.getResource())).collect(Collectors.toList()));
    }
    return tsResult;
  }

  private FhirQueryParams mapFhirParams(ValueSetQueryParams tsParams) {
    FhirQueryParams fParams = new FhirQueryParams();
    fParams.putSingle(FhirQueryParams.count, tsParams.getLimit().toString());
    fParams.setOffset(tsParams.getOffset());
    return fParams;
  }

}
