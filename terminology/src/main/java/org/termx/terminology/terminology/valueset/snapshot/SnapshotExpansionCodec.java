package org.termx.terminology.terminology.valueset.snapshot;

import com.kodality.commons.util.JsonUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.termx.ts.valueset.ValueSetVersionConcept;

/**
 * Encodes/decodes a value set expansion as gzip-compressed JSON for storage in the
 * {@code value_set_snapshot.expansion_bytea} column.
 *
 * <p>A raw {@code jsonb} value is capped near 1 GB by Postgres, which made large expansions
 * unstorable (issue #36). Expansion JSON is extremely repetitive (the same keys per concept), so
 * gzip shrinks it ~10-20x and lifts the practical ceiling well above the reported 1.7 GB case.
 */
public final class SnapshotExpansionCodec {
  private SnapshotExpansionCodec() {}

  public static byte[] encode(List<ValueSetVersionConcept> expansion) {
    byte[] json = JsonUtil.toJson(expansion == null ? List.of() : expansion).getBytes(StandardCharsets.UTF_8);
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, json.length / 8));
         GZIPOutputStream gz = new GZIPOutputStream(baos)) {
      gz.write(json);
      gz.finish();
      return baos.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("failed to compress value set expansion", e);
    }
  }

  public static List<ValueSetVersionConcept> decode(byte[] gz) {
    return JsonUtil.fromJson(decodeToJson(gz), JsonUtil.getListType(ValueSetVersionConcept.class));
  }

  public static String decodeToJson(byte[] gz) {
    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("failed to decompress value set expansion", e);
    }
  }
}
