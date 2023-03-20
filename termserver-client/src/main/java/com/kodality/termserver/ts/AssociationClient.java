package com.kodality.termserver.ts;

import com.kodality.commons.client.BaseHttpClient;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ts.association.AssociationType;
import com.kodality.termserver.ts.association.AssociationTypeQueryParams;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class AssociationClient {
  protected HttpClient client;

  public AssociationClient(){}

  public AssociationClient(String url, Function<String, HttpClient> clientProvider) {
    client = clientProvider.apply(url + "/ts/association-types");
  }

  public CompletableFuture<QueryResult<AssociationType>> queryAssociationTypes(AssociationTypeQueryParams params) {
    return client.GET("?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, AssociationType.class));
  }

  public CompletableFuture<AssociationType> getAssociationType(String code) {
    return client.GET("/" + code, AssociationType.class);
  }

}
