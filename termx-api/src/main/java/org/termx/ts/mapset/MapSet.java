package org.termx.ts.mapset;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import org.termx.commons.UniqueResource;
import org.termx.ts.ConfigurationAttribute;
import org.termx.ts.ContactDetail;
import org.termx.ts.Copyright;
import org.termx.ts.OtherTitle;
import org.termx.ts.Topic;
import org.termx.ts.UseContext;
import io.micronaut.core.annotation.Introspected;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class MapSet extends UniqueResource<MapSet> {
  private String uri;
  private String publisher;
  private String name;
  private List<OtherTitle> otherTitle;
  private LocalizedName title;
  private LocalizedName description;
  private LocalizedName purpose;
  private Topic topic;
  private List<UseContext> useContext;
  private String narrative;
  private Boolean experimental;
  private String sourceReference;
  private String replaces;
  private String externalWebSource;
  private List<Identifier> identifiers;
  private List<ConfigurationAttribute> configurationAttributes;
  private List<ContactDetail> contacts;
  private Copyright copyright;

  private MapSetSettings settings;

  private List<MapSetVersion> versions;
  private List<MapSetProperty> properties;

  @Getter
  @Setter
  public static class MapSetSettings {
    private boolean reviewRequired;
    private boolean approvalRequired;
  }
}
