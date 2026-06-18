package org.termx.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kodality.commons.util.JsonUtil;
import com.kodality.kefhir.core.api.resource.ResourceBeforeSaveInterceptor;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.structure.api.ResourceContent;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Normalizes incoming CodeSystem / ValueSet / ConceptMap JSON before it reaches instance validation,
 * so TermX accepts a few valid-or-near-valid shapes the base validator would otherwise reject. Runs in the
 * INPUT_VALIDATION phase ordered ahead of the validator (lower {@link Order}); it mutates the shared
 * {@link ResourceContent}, so both the validator and the importer see the normalized resource.
 *
 * <ul>
 *   <li><b>status</b> — FHIR requires it (1..1); when absent, default to {@code draft} rather than reject.</li>
 *   <li><b>supplement caseSensitive</b> — a CodeSystem supplement SHOULD NOT state {@code caseSensitive}
 *       (it inherits from the base); drop the stated value so it defaults to base behavior.</li>
 *   <li><b>language-tagged shorthand</b> — fold {@code "<field>:<lang>": "x"} (e.g. {@code definition:en})
 *       into the standard {@code _<field>.extension} translation, then drop the non-standard colon-key the
 *       strict parser cannot read. Supports multilingual definitions/displays carried on the {@code :} form.</li>
 * </ul>
 */
@Slf4j
@Singleton
@Order(-100)
@Requires(property = "kefhir.validation-profile.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class TerminologyResourceNormalizer extends ResourceBeforeSaveInterceptor {
  private static final List<String> TERMINOLOGY_TYPES = List.of("CodeSystem", "ValueSet", "ConceptMap");
  private static final String TRANSLATION_EXTENSION = "http://hl7.org/fhir/StructureDefinition/translation";

  public TerminologyResourceNormalizer() {
    super(ResourceBeforeSaveInterceptor.INPUT_VALIDATION);
  }

  @Override
  public void handle(ResourceId id, ResourceContent content, String interaction) {
    if (!TERMINOLOGY_TYPES.contains(id.getResourceType()) || StringUtils.isEmpty(content.getValue())) {
      return;
    }
    try {
      ObjectMapper om = JsonUtil.getObjectMapper();
      JsonNode root = om.readTree(content.getValue());
      if (!(root instanceof ObjectNode obj)) {
        return;
      }
      boolean changed = defaultStatus(obj);
      changed |= dropSupplementCaseSensitive(obj);
      changed |= foldLanguageTaggedShorthand(obj);
      if (changed) {
        content.setValue(om.writeValueAsString(obj));
      }
    } catch (Exception e) {
      // Normalization is best-effort: never block a save because the pre-pass failed — let validation speak.
      log.debug("terminology resource normalization skipped: {}", e.getMessage());
    }
  }

  private static boolean defaultStatus(ObjectNode resource) {
    if (!resource.hasNonNull("status")) {
      resource.put("status", "draft");
      return true;
    }
    return false;
  }

  private static boolean dropSupplementCaseSensitive(ObjectNode resource) {
    if ("supplement".equals(resource.path("content").asText(null)) && resource.has("caseSensitive")) {
      resource.remove("caseSensitive");
      return true;
    }
    return false;
  }

  /**
   * Recursively folds {@code "<field>:<lang>"} keys into {@code _<field>.extension[translation]} and removes
   * them. The base value ({@code <field>}) is left untouched; only the language variants move to the standard
   * translation extension, which the mapper already understands.
   */
  private static boolean foldLanguageTaggedShorthand(ObjectNode node) {
    boolean changed = false;
    List<String> langKeys = new ArrayList<>();
    Iterator<String> names = node.fieldNames();
    while (names.hasNext()) {
      String n = names.next();
      // a language-tagged primitive shorthand: name contains ':' and the part after it looks like a lang code
      int i = n.indexOf(':');
      if (i > 0 && i < n.length() - 1 && node.get(n).isValueNode()) {
        langKeys.add(n);
      }
    }
    for (String key : langKeys) {
      String field = key.substring(0, key.indexOf(':'));
      String lang = key.substring(key.indexOf(':') + 1);
      String value = node.get(key).asText();
      addTranslation(node, field, lang, value);
      node.remove(key);
      changed = true;
    }
    // recurse into children (concepts, contains, etc.)
    for (JsonNode child : node) {
      if (child instanceof ObjectNode obj) {
        changed |= foldLanguageTaggedShorthand(obj);
      } else if (child.isArray()) {
        for (JsonNode el : child) {
          if (el instanceof ObjectNode obj) {
            changed |= foldLanguageTaggedShorthand(obj);
          }
        }
      }
    }
    return changed;
  }

  private static void addTranslation(ObjectNode node, String field, String lang, String value) {
    String primitive = "_" + field;
    ObjectNode underscore = node.has(primitive) && node.get(primitive).isObject()
        ? (ObjectNode) node.get(primitive) : node.putObject(primitive);
    com.fasterxml.jackson.databind.node.ArrayNode extensions = underscore.has("extension") && underscore.get("extension").isArray()
        ? (com.fasterxml.jackson.databind.node.ArrayNode) underscore.get("extension") : underscore.putArray("extension");
    // skip if a translation for this language is already present (the standard form wins; no duplication)
    for (JsonNode ext : extensions) {
      if (TRANSLATION_EXTENSION.equals(ext.path("url").asText(null)) && hasLang(ext, lang)) {
        return;
      }
    }
    ObjectNode translation = extensions.addObject();
    translation.put("url", TRANSLATION_EXTENSION);
    com.fasterxml.jackson.databind.node.ArrayNode inner = translation.putArray("extension");
    inner.addObject().put("url", "lang").put("valueCode", lang);
    inner.addObject().put("url", "content").put("valueString", value);
  }

  private static boolean hasLang(JsonNode translationExt, String lang) {
    for (JsonNode e : translationExt.path("extension")) {
      if ("lang".equals(e.path("url").asText(null)) && lang.equals(e.path("valueCode").asText(null))) {
        return true;
      }
    }
    return false;
  }
}
