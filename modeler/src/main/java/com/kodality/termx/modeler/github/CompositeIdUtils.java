package com.kodality.termx.modeler.github;

import com.kodality.termx.core.fhir.BaseFhirMapper;
import jakarta.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@UtilityClass
public class CompositeIdUtils {

  /**
   *
   * @param fileName file name without extension. ex. BloodPressure--0.0.1
   * @return
   */
  public static CompositeId parseCompositeId(String fileName) {
    String[] parts = BaseFhirMapper.parseCompositeId(fileName);
    return new CompositeId(parts[0], parts[1]);
  }

  /**
   *
   * @param fileName file name with extension. ex. BloodPressure--0.0.1.json
   * @param extension file extension. ex. .json
   * @return
   */
  public static CompositeId parseCompositeId(String fileName, String extension) {
    final String cleanName = StringUtils.removeEnd(fileName, "." + StringUtils.removeStart(extension, "."));
    return parseCompositeId(cleanName);
  }

  @NotNull
  public static String getFhirId(String code, String version) {
    return Stream.of(code, version)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.joining(BaseFhirMapper.SEPARATOR));
  }

  public record CompositeId(String code, String version) {
  }
}
