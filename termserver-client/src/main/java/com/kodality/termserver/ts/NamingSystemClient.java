package com.kodality.termserver.ts;

import com.kodality.commons.client.BaseHttpClient;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ts.namingsystem.NamingSystem;
import com.kodality.termserver.ts.namingsystem.NamingSystemQueryParams;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class NamingSystemClient {
  protected HttpClient client;

  public NamingSystemClient(){}

  public NamingSystemClient(String url, Function<String, HttpClient> clientProvider) {
    client = clientProvider.apply(url + "/ts/naming-systems");
  }

  public CompletableFuture<QueryResult<NamingSystem>> queryNamingSystems(NamingSystemQueryParams params) {
    return client.GET("?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, NamingSystem.class));
  }

  public CompletableFuture<NamingSystem> getNamingSystem(String namingSystem) {
    return client.GET("/" + namingSystem, NamingSystem.class);
  }
}
