package com.kodality.termx.core.utils;

import com.kodality.termx.core.utils.PatchUtil.PatchRequest.PatchField;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

public class PatchUtil {

  public static <T> T mergeFields(PatchRequest request, T obj) {
    Class<?> clas = obj.getClass();
    Field[] fields = clas.getDeclaredFields();
    Object result = null;
    try {
      result = clas.getDeclaredConstructor().newInstance();
      for (Field field : fields) {
        field.setAccessible(true);
        Optional<PatchField> patchField = request.getFields().stream().filter(f -> f.getFieldName().equals(field.getName())).findFirst();
        if (patchField.isPresent()) {
          field.set(result, patchField.get().getValue());
        } else {
          field.set(result, field.get(obj));
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return (T) result;
  }

  @Getter
  @Setter
  public static class PatchRequest {
    private List<PatchField> fields;

    @Getter
    @Setter
    public static class PatchField {
      private String fieldName;
      private Object value;
    }
  }
}
