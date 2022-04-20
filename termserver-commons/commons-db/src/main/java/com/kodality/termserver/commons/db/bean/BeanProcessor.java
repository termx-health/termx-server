package com.kodality.termserver.commons.db.bean;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.dbutils.GenerousBeanProcessor;
import org.apache.commons.lang3.StringUtils;

import static java.lang.String.format;

public class BeanProcessor extends GenerousBeanProcessor {
  private final Map<String, ColumnProcessor> columnProcessors = new HashMap<>();
  private final Map<String, String> columnToPropertyOverrides = new HashMap<>();
  private final Map<String, RowProcessor> rowProcessors = new HashMap<>();
  private final List<String> ignoreProperties = new ArrayList<>();
  private static final Map<String, SoftReference<Method>> setterMethodCache = new HashMap<>();
  private final Class<?> beanClass;
  private String columnPrefix;

  private final ColumnProcessor defaultColumnProcessor = BeanProcessor.super::processColumn;

  public BeanProcessor(Class<?> beanClass) {
    this.beanClass = beanClass;
  }

  public Class<?> getBeanClass() {
    return beanClass;
  }

  public <T extends BeanProcessor> T setColumnPrefix(String columnPrefix) {
    this.columnPrefix = columnPrefix;
    return getThis();
  }

  public <T extends BeanProcessor> T addColumnProcessor(String dbColumn, ColumnProcessor processor) {
    columnProcessors.put(dbColumn, processor);
    return getThis();
  }

  public <T extends BeanProcessor> T addRowProcessor(String beanProperty, RowProcessor processor) {
    rowProcessors.put(beanProperty, processor);
    ignoreProperties.add(beanProperty);
    return getThis();
  }

  public void ignoreProperty(String beanProperty) {
    ignoreProperties.add(beanProperty);
  }

  public <T extends BeanProcessor> T addColumnProcessor(String dbColumn,
                                                        String beanProperty,
                                                        ColumnProcessor processor) {
    addColumnProcessor(dbColumn, processor);
    overrideColumnMapping(dbColumn, beanProperty);
    return getThis();
  }

  public <T extends BeanProcessor> T overrideColumnMapping(String dbColumn, String beanProperty) {
    columnToPropertyOverrides.put(dbColumn, beanProperty);
    return getThis();
  }

  @SuppressWarnings("unchecked")
  protected <T extends BeanProcessor> T getThis() {
    return (T) this;
  }

  @SuppressWarnings("unchecked")
  public <T> T toBean(ResultSet rs) throws SQLException {
    return (T) toBean(rs, getBeanClass());
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> toBeanList(ResultSet rs) throws SQLException {
    return (List<T>) toBeanList(rs, getBeanClass());
  }

  @Override
  protected <T> T populateBean(ResultSet rs, T bean, PropertyDescriptor[] props, int[] columnToProperty)
      throws SQLException {
    bean = super.populateBean(rs, bean, props, columnToProperty);
    invokeRowProcessors(rs, bean);
    return bean;
  }

  private <T> void invokeRowProcessors(ResultSet rs, T bean) throws SQLException {
    for (String property : rowProcessors.keySet()) {
      RowProcessor processor = rowProcessors.get(property);
      Object data = processor.process(rs);
      if(data == null) {
        continue;
      }

      Method writeMethod = getWriteMethod(bean, property, data);
      if (writeMethod == null) {
        throw new SQLException("Cannot set " + property + ": could not find matching setter in class " + bean.getClass().getName());
      }
      try {
        writeMethod.invoke(bean, data);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        throw new SQLException("Cannot set " + property + ": " + e.getMessage());
      }
    }
  }

  @Override
  protected int[] mapColumnsToProperties(final ResultSetMetaData rsmd, final PropertyDescriptor[] props)
      throws SQLException {

    final int cols = rsmd.getColumnCount();
    final int[] columnToProperty = new int[cols + 1];
    Arrays.fill(columnToProperty, PROPERTY_NOT_FOUND);

    for (int col = 1; col <= cols; col++) {
      String columnName = rsmd.getColumnLabel(col);

      if (null == columnName || 0 == columnName.length()) {
        columnName = rsmd.getColumnName(col);
      }

      if (columnName != null) {
        columnName = columnName.toLowerCase();
      }

      if (columnPrefix != null && !columnName.startsWith(columnPrefix)) {
        continue;
      }
      String columnNameMatcher = columnPrefix == null ? columnName : columnName.replaceFirst(columnPrefix, "");

      for (int i = 0; i < props.length; i++) {
        final String propName = props[i].getName();
        if (ignoreProperties.contains(propName)) {
          continue;
        }
        // see if either the column name, or the generous one matches
        if (columnNameMatcher.equalsIgnoreCase(propName)
            || getGenerousColumnName(columnNameMatcher).equalsIgnoreCase(propName)) {
          columnToProperty[col] = i;
          break;
        }
      }
    }

    return columnToProperty;
  }

  protected String getGenerousColumnName(String columnName) {
    return columnToPropertyOverrides.getOrDefault(columnName, columnName).replace("_", "");
  }

  @Override
  protected Object processColumn(ResultSet rs, int index, Class<?> propType) throws SQLException {
    String columnName = rs.getMetaData().getColumnName(index).toLowerCase();
    columnName = columnPrefix == null ? columnName : columnName.replaceFirst(columnPrefix, "");
    try {
      return columnProcessors.getOrDefault(columnName, defaultColumnProcessor).processColumn(rs, index, propType);
    } catch (SQLException e) {
      throw new RuntimeException(format("Failed to read column '%s'", columnName), e);
    }
  }

  protected Method getWriteMethod(Object target, String propertyName, Object propValue) {
    try {
      PropertyDescriptor propertyDescriptor =
          Arrays.stream(Introspector.getBeanInfo(target.getClass()).getPropertyDescriptors()).filter(x -> x.getName().equals(propertyName)).findFirst()
              .orElse(null);
      if (propertyDescriptor == null) {
        return null;
      }
      return getWriteMethod(target, propertyDescriptor, propValue);
    } catch (IntrospectionException e) {
      return null;
    }
  }

  @Override
  protected Method getWriteMethod(Object target, PropertyDescriptor prop, Object value) {
    Method writeMethod = super.getWriteMethod(target, prop, value);
    if (writeMethod != null) {
      return writeMethod;
    }
    if (value == null) {
      return null;
    }

    String setterName = "set" + StringUtils.capitalize(prop.getName());
    String cacheKey = target.getClass().getName() + "#" + setterName;
    if (setterMethodCache.containsKey(cacheKey)) {
      SoftReference<Method> reference = setterMethodCache.get(cacheKey);
      if (reference.get() != null) {
        return reference.get();
      }
    }
    Method method = findDeclaredMethod(target.getClass(), setterName, prop.getPropertyType());
    if (method != null) {
      setterMethodCache.put(cacheKey, new SoftReference<>(method));
    }
    return method;
  }

  private static Method findDeclaredMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
    try {
      return clazz.getDeclaredMethod(methodName, paramTypes);
    } catch (NoSuchMethodException ex) {
      if (clazz.getSuperclass() != null) {
        return findDeclaredMethod(clazz.getSuperclass(), methodName, paramTypes);
      }
      return null;
    }
  }
}

