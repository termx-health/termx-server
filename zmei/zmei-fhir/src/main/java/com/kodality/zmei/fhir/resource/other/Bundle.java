package com.kodality.zmei.fhir.resource.other;

import com.kodality.zmei.fhir.BackboneElement;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Reference;
import com.kodality.zmei.fhir.datatypes.Signature;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.util.Lists;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import static java.util.stream.Collectors.toList;

@Getter
@Setter
@Accessors(chain = true)
public class Bundle extends Resource {
  private Identifier identifier;
  private String type;
  private OffsetDateTime timestamp;
  private Integer total;
  private List<BundleLink> link;
  private List<BundleEntry> entry;
  private Signature signature;
  private List<Resource> issues;

  public Bundle() {
    super("Bundle");
  }

  public static Bundle of(String type, List<? extends DomainResource> resp) {
    Bundle bundle = new Bundle();
    bundle.setType(type);
    if (resp == null || resp.isEmpty()) {
      bundle.setTotal(0);
      bundle.setEntry(Collections.emptyList());
      return bundle;
    }
    bundle.setTotal(resp.size());
    bundle.setEntry(resp.stream().map(r -> {
      Bundle.BundleEntry entry = new Bundle.BundleEntry();
      entry.setResource(r);
      return entry;
    }).collect(toList()));
    return bundle;
  }

  public Bundle addEntry(BundleEntry entry) {
    this.entry = Lists.add(this.entry, entry);
    return this;
  }

  public Reference addEntry(String method, Resource resource) {
    return addEntry(null, method, resource);
  }

  public Reference addEntry(String fullUrl, String method, Resource resource) {
    String requestUrl = method.equals("POST") ? resource.getResourceType() : resource.getResourceType() + "/" + resource.getId();
    this.entry = Lists.add(this.entry, new BundleEntry(resource, new BundleEntryRequest(method, requestUrl)).setFullUrl(fullUrl));
    return new Reference(fullUrl);
  }

  public Bundle addLink(BundleLink link) {
    this.link = Lists.add(this.link, link);
    return this;
  }


  @Getter
  @Setter
  @Accessors(chain = true)
  public static class BundleLink extends BackboneElement {
    private String relation;
    private String url;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class BundleEntry extends BackboneElement {
    private List<BundleLink> link;
    private String fullUrl;
    private Resource resource;
    private BundleEntrySearch search;
    private BundleEntryRequest request;
    private BundleEntryResponse response;

    public BundleEntry() {
    }

    public BundleEntry(Resource resource, BundleEntryRequest request) {
      this.resource = resource;
      this.request = request;
    }

    @SuppressWarnings("unchecked")
    public <T> T getResource() {
      return (T) resource;
    }
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class BundleEntrySearch extends BackboneElement {
    private String mode;
    private BigDecimal score;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class BundleEntryRequest extends BackboneElement {
    private String method;
    private String uri;
    private String ifNoneMatch;
    private OffsetDateTime ifModifiedSince;
    private String ifMatch;
    private String ifNoneExist;

    public BundleEntryRequest() {
    }

    public BundleEntryRequest(String method, String uri) {
      this.method = method;
      this.uri = uri;
    }
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class BundleEntryResponse extends BackboneElement {
    private String status;
    private String location;
    private String etag;
    private OffsetDateTime lastModified;
    private Resource resource;
  }
}
