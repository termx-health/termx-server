package org.termx.ts;

import com.kodality.commons.client.BaseHttpClient;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import org.termx.ts.mapset.MapSet;
import org.termx.ts.mapset.MapSetAssociation;
import org.termx.ts.mapset.MapSetAssociationQueryParams;
import org.termx.ts.mapset.MapSetQueryParams;
import org.termx.ts.mapset.MapSetVersion;
import org.termx.ts.mapset.MapSetVersionQueryParams;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class MapSetClient {
  protected HttpClient client;

  public MapSetClient(){}

  public MapSetClient(String url, Function<String, HttpClient> clientProvider) {
    client = clientProvider.apply(url + "/ts/map-sets");
  }

  public CompletableFuture<QueryResult<MapSet>> queryMapSets(MapSetQueryParams params) {
    return client.GET("?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, MapSet.class));
  }

  public CompletableFuture<MapSet> getMapSet(String mapSet) {
    return client.GET("/" + mapSet, MapSet.class);
  }

  public CompletableFuture<QueryResult<MapSetVersion>> queryMapSetVersions(String mapSet, MapSetVersionQueryParams params) {
    return client.GET("/" + mapSet + "/versions?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, MapSetVersion.class));
  }

  public CompletableFuture<MapSetVersion> getMapSetVersion(String mapSet, String version) {
    return client.GET("/" + mapSet + "/versions/" + version, MapSetVersion.class);
  }

  public CompletableFuture<QueryResult<MapSetAssociation>> queryAssociations(String mapSet, MapSetAssociationQueryParams params) {
    return client.GET("/" + mapSet + "/associations?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, MapSetAssociation.class));
  }

  public CompletableFuture<MapSetAssociation> getAssociation(String mapSet, Long id) {
    return client.GET("/" + mapSet + "/associations/" + id, MapSetAssociation.class);
  }

}
