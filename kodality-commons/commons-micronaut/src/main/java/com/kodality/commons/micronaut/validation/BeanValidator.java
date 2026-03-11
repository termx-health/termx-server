package com.kodality.commons.micronaut.validation;

import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class BeanValidator {
  private final Validator validator;

  public BeanValidator() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  public <T> void validate(T resource, Class<?>... groups) {
    Set<ConstraintViolation<T>> violations = validator.validate(resource, getGroups(groups));
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  public <T> void validateProperties(T resource, String... properties) {
    Set<ConstraintViolation<T>> violations =
        Stream.of(properties).flatMap(p -> validator.validateProperty(resource, p).stream()).collect(Collectors.toSet());
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  protected static Class<?>[] getGroups(Class<?>[] groups) {
    ArrayList<Class<?>> validationGroups = new ArrayList<>();
    validationGroups.add(Default.class);
    if (groups != null && groups.length > 0) {
      validationGroups.addAll(Arrays.asList(groups));
    }
    return validationGroups.toArray(new Class<?>[validationGroups.size()]);
  }

}
