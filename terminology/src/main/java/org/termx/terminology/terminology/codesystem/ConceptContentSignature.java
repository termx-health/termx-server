package org.termx.terminology.terminology.codesystem;

import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystemAssociation;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.codesystem.EntityProperty;
import org.termx.ts.codesystem.EntityPropertyType;
import org.termx.ts.codesystem.EntityPropertyValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Canonical, type-aware content signature of a code system concept version, used by import to decide
 * whether a concept actually changed (and therefore whether a new entity version is needed).
 *
 * <p>The whole point is that the SAME normalization is applied to both sides — the freshly parsed
 * import value and the value reloaded from the database — so that formatting-only differences never
 * count as a change:
 * <ul>
 *   <li>decimal — compared by value, ignoring scale ({@code 2.5} == {@code 2.50})</li>
 *   <li>integer — {@code 5} == {@code "5"} == {@code 5.0}</li>
 *   <li>boolean — {@code true} == {@code "1"} == {@code "true"}</li>
 *   <li>dateTime — normalized to a UTC instant, so {@link Date}, epoch millis and ISO strings
 *       (date-only or with offset) of the same moment compare equal</li>
 *   <li>code/Coding — compared by {@code code|system} (display/version excluded)</li>
 * </ul>
 * Designation/property/association sets are order-independent (sorted before joining).
 */
public final class ConceptContentSignature {
  private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

  private ConceptContentSignature() {}

  public static boolean sameContent(CodeSystemEntityVersion a, CodeSystemEntityVersion b, Map<Long, EntityProperty> propertiesById) {
    return of(a, propertiesById).equals(of(b, propertiesById));
  }

  public static String of(CodeSystemEntityVersion version, Map<Long, EntityProperty> propertiesById) {
    if (version == null) {
      return "";
    }
    Map<Long, EntityProperty> props = propertiesById == null ? Map.of() : propertiesById;

    // Compared in a CANONICAL order, not the list order: the DB load order is not guaranteed
    // (designations/associations have no ORDER BY), so relying on it would make unchanged concepts
    // look changed. Designations are ordered by language then text; properties/associations by their
    // identity. Identity uses CODES, never internal DB ids (the file only carries codes).
    List<String> designations = Optional.ofNullable(version.getDesignations()).orElse(List.of()).stream()
        .filter(d -> d != null && !PublicationStatus.retired.equals(d.getStatus()))
        .map(d -> "D|" + nz(d.getLanguage()) + "|" + nz(trim(d.getName())) + "|" + designationTypeCode(d, props) + "|" + bool(d.isPreferred()))
        .sorted()
        .toList();

    List<String> properties = Optional.ofNullable(version.getPropertyValues()).orElse(List.of()).stream()
        .filter(Objects::nonNull)
        .map(pv -> {
          EntityProperty p = props.get(pv.getEntityPropertyId());
          String code = p != null ? p.getName() : pv.getEntityProperty() != null ? pv.getEntityProperty() : String.valueOf(pv.getEntityPropertyId());
          String type = p != null ? p.getType() : pv.getEntityPropertyType();
          return "P|" + code + "|" + normalize(pv.getValue(), type);
        })
        .sorted()
        .toList();

    List<String> associations = Optional.ofNullable(version.getAssociations()).orElse(List.of()).stream()
        .filter(Objects::nonNull)
        .map(a -> "A|" + nz(a.getAssociationType()) + "|" + nz(a.getTargetCode()) + "|" + (a.getOrderNumber() == null ? "" : a.getOrderNumber()))
        .sorted()
        .toList();

    return "code=" + nz(trim(version.getCode()))
        + "\ndesignations=" + designations
        + "\nproperties=" + properties
        + "\nassociations=" + associations;
  }

  /** Type-aware normalization of a single property value to a canonical token. */
  static String normalize(Object value, String type) {
    if (value == null) {
      return "";
    }
    String s = String.valueOf(value).trim();
    if (type == null) {
      return s;
    }
    switch (type) {
      case EntityPropertyType.bool:
        return bool("1".equals(s) || "true".equalsIgnoreCase(s) || Boolean.TRUE.equals(value));
      case EntityPropertyType.integer:
        try {
          return new BigDecimal(s).toBigInteger().toString();
        } catch (NumberFormatException e) {
          return s;
        }
      case EntityPropertyType.decimal:
        try {
          BigDecimal bd = new BigDecimal(s);
          return bd.signum() == 0 ? "0" : bd.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
          return s;
        }
      case EntityPropertyType.dateTime:
        return normalizeDateTime(value, s);
      case EntityPropertyType.code:
      case EntityPropertyType.coding:
        return normalizeCoding(value, s);
      default:
        return s;
    }
  }

  private static String normalizeDateTime(Object value, String s) {
    Instant instant = null;
    if (value instanceof Date d) {
      instant = d.toInstant();
    } else if (value instanceof Number n) {
      instant = Instant.ofEpochMilli(n.longValue());
    } else {
      instant = parseInstant(s);
    }
    return instant == null ? s : ISO.format(instant.truncatedTo(ChronoUnit.SECONDS));
  }

  private static Instant parseInstant(String s) {
    if (s.isEmpty()) {
      return null;
    }
    try {
      return Instant.ofEpochMilli(Long.parseLong(s));
    } catch (NumberFormatException ignored) {
      // not epoch millis
    }
    try {
      return Instant.parse(s);
    } catch (RuntimeException ignored) {
    }
    try {
      return OffsetDateTime.parse(s).toInstant();
    } catch (RuntimeException ignored) {
    }
    try {
      return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);
    } catch (RuntimeException ignored) {
    }
    try {
      return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant();
    } catch (RuntimeException ignored) {
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static String normalizeCoding(Object value, String s) {
    if (value instanceof EntityPropertyValue.EntityPropertyValueCodingValue c) {
      return nz(c.getCode()) + "|" + nz(c.getCodeSystem());
    }
    if (value instanceof Map<?, ?> m) {
      Object system = m.containsKey("codeSystem") ? m.get("codeSystem") : m.get("system");
      return nz(str(m.get("code"))) + "|" + nz(str(system));
    }
    return s + "|";
  }

  /** Designation type as a CODE (the entity-property name), populated on both sides; never the id. */
  private static String designationTypeCode(Designation d, Map<Long, EntityProperty> props) {
    if (d.getDesignationType() != null) {
      return d.getDesignationType();
    }
    EntityProperty p = d.getDesignationTypeId() == null ? null : props.get(d.getDesignationTypeId());
    return p != null ? nz(p.getName()) : d.getDesignationTypeId() == null ? "" : String.valueOf(d.getDesignationTypeId());
  }

  private static String bool(boolean b) {
    return b ? "true" : "false";
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  private static String trim(String s) {
    return s == null ? null : s.trim();
  }

  private static String str(Object o) {
    return o == null ? null : String.valueOf(o);
  }
}
