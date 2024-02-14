package org.folio.fqm.service;

import org.folio.fql.model.*;
import org.folio.fql.service.FqlService;
import org.folio.fqm.exception.ColumnNotFoundException;
import org.folio.fqm.utils.SqlFieldIdentificationUtils;
import org.folio.querytool.domain.dto.DateType;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeColumn;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiFunction;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.cast;
import static org.jooq.impl.DSL.cardinality;
import static org.jooq.impl.DSL.condition;
import static org.jooq.impl.DSL.falseCondition;
import static org.jooq.impl.DSL.or;
import static org.jooq.impl.DSL.param;
import static org.jooq.impl.DSL.val;

/**
 * Class responsible for converting an FQL query to SQL query
 */
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class FqlToSqlConverterService {

  /**
   * This regex matches the ISO_LOCAL_DATE format. Required: yyyy-MM-dd.
   * If datetime is provided in this form, a date-wise comparison will be performed. In other words,
   * any record matching the provided date will be retrieved. If datetime is provided in any other form,
   * an exact comparison will be performed.
   */
  private static final String DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$";
  private static final String STRING_TYPE = "stringType";
  private static final String RANGED_UUID_TYPE = "rangedUUIDType";
  private static final String OPEN_UUID_TYPE = "openUUIDType";
  private static final String DATE_TYPE = "dateType";

  private final FqlService fqlService;

  /**
   * Converts the given FQL query string to the corresponding SQL query
   */
  public Condition getSqlCondition(String fqlCriteria, EntityType entityType) {
    FqlCondition<?> fqlCondition = fqlService.getFql(fqlCriteria).fqlCondition();
    return getSqlCondition(fqlCondition, entityType);
  }

  /**
   * Converts the given FQL condition to the corresponding SQL query
   */
  public static Condition getSqlCondition(FqlCondition<?> fqlCondition, EntityType entityType) {
    final Field<Object> field = fqlCondition instanceof FieldCondition<?> fieldCondition
      ? field(fieldCondition, entityType)
      : null;

    // TODO: make this better
    // I don't love the casts here, but the only way to avoid that is a massive chain of if-else statements
    // with instanceof, and that'd be well over double the size. Hopefully JDK 21 will bring some improvements...
    return switch (fqlCondition.getClass().getSimpleName()) {
      case "EqualsCondition" -> handleEquals((EqualsCondition) fqlCondition, entityType, field);
      case "NotEqualsCondition" -> handleNotEquals((NotEqualsCondition) fqlCondition, entityType, field);
      case "InCondition" -> handleIn((InCondition) fqlCondition, entityType, field);
      case "NotInCondition" -> handleNotIn((NotInCondition) fqlCondition, entityType, field);
      case "GreaterThanCondition" -> handleGreaterThan((GreaterThanCondition) fqlCondition, entityType, field);
      case "LessThanCondition" -> handleLessThan((LessThanCondition) fqlCondition, entityType, field);
      case "AndCondition" -> handleAnd((AndCondition) fqlCondition, entityType);
      case "RegexCondition" -> handleRegEx((RegexCondition) fqlCondition, field);
      case "ContainsCondition" -> handleContains((ContainsCondition) fqlCondition, entityType, field);
      case "NotContainsCondition" -> handleNotContains((NotContainsCondition) fqlCondition, entityType, field);
      case "EmptyCondition" -> handleEmpty((EmptyCondition) fqlCondition, entityType, field);
      default -> falseCondition();
    };
  }

  private static Condition handleEquals(EqualsCondition equalsCondition, EntityType entityType, Field<Object> field) {
    if (isDateCondition(equalsCondition, entityType)) {
      return handleDate(equalsCondition, field);
    }
    String dataType = getColumnDataType(entityType, equalsCondition);
    if (STRING_TYPE.equals(dataType) || RANGED_UUID_TYPE.equals(dataType) || OPEN_UUID_TYPE.equals(dataType) || DATE_TYPE.equals(dataType))  {
      return caseInsensitiveComparison(equalsCondition, entityType, field, (String) equalsCondition.value(), Field::equalIgnoreCase, Field::eq);
    }
    return field.eq(valueField(equalsCondition.value(), equalsCondition, entityType));
  }

  private static Condition handleNotEquals(NotEqualsCondition notEqualsCondition, EntityType entityType, Field<Object> field) {
    if (isDateCondition(notEqualsCondition, entityType)) {
      return handleDate(notEqualsCondition, field);
    }
    String dataType = getColumnDataType(entityType, notEqualsCondition);
    if (STRING_TYPE.equals(dataType) || RANGED_UUID_TYPE.equals(dataType) || OPEN_UUID_TYPE.equals(dataType) || DATE_TYPE.equals(dataType)) {
      return caseInsensitiveComparison(notEqualsCondition, entityType, field, (String) notEqualsCondition.value(), Field::notEqualIgnoreCase, Field::ne);
    }
    return field.ne(valueField(notEqualsCondition.value(), notEqualsCondition, entityType));
  }

  private static Condition handleDate(FieldCondition<?> fieldCondition, Field<Object> field) {
    Condition condition = falseCondition();
    var dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
    var date = LocalDate.parse((String) fieldCondition.value(), dateTimeFormatter);
    LocalDate nextDay = date.plusDays(1);
    if (fieldCondition instanceof EqualsCondition) {
      condition = field.greaterOrEqual(date.toString())
        .and(field.lessThan(nextDay.toString()));
    } else if (fieldCondition instanceof NotEqualsCondition) {
      condition = field.greaterOrEqual(nextDay.toString())
        .or(field.lessThan(date.toString()));
    } else if (fieldCondition instanceof GreaterThanCondition greaterThanCondition) {
      if (greaterThanCondition.orEqualTo()) {
        condition = field.greaterOrEqual(date.toString());
      } else {
        condition = field.greaterOrEqual(nextDay.toString());
      }
    } else if (fieldCondition instanceof LessThanCondition lessThanCondition) {
      if (lessThanCondition.orEqualTo()) {
        condition = field.lessThan(nextDay.toString());
      } else {
        condition = field.lessThan(date.toString());
      }
    }
    return condition;
  }

  private static EntityTypeColumn getColumn(FieldCondition<?> fieldCondition, EntityType entityType) {
    return entityType.getColumns()
      .stream()
      .filter(col -> col.getName().equals(fieldCondition.fieldName()))
      .findFirst()
      .orElseThrow(() -> new ColumnNotFoundException(entityType.getName(), fieldCondition.fieldName()));
  }

  private static boolean isDateCondition(FieldCondition<?> fieldCondition, EntityType entityType) {
    EntityDataType dataType = getColumn(fieldCondition, entityType).getDataType();
    return dataType instanceof DateType
      && ((String) fieldCondition.value()).matches(DATE_REGEX);
  }

  private static Condition handleIn(InCondition inCondition, EntityType entityType, Field<Object> field) {
    List<Condition> conditionList = inCondition
      .value().stream()
      .map(val -> {
        if (val instanceof String value) {
          return caseInsensitiveComparison(inCondition, entityType, field, value, Field::equalIgnoreCase, Field::eq);
        } else {
          return field.eq(valueField(val, inCondition, entityType));
        }
      })
      .toList();

    return or(conditionList);
  }

  private static Condition handleNotIn(NotInCondition notInCondition, EntityType entityType, Field<Object> field) {
    List<Condition> conditionList = notInCondition
      .value().stream()
      .map(val -> {
        if (val instanceof String value) {
          return caseInsensitiveComparison(notInCondition, entityType, field, value, Field::notEqualIgnoreCase, Field::notEqual);
        } else {
          return field.notEqual(valueField(val, notInCondition, entityType));
        }
      })
      .toList();
    return and(conditionList);
  }

  private static Condition handleGreaterThan(GreaterThanCondition greaterThanCondition, EntityType entityType, Field<Object> field) {
    if (isDateCondition(greaterThanCondition, entityType)) {
      return handleDate(greaterThanCondition, field);
    }
    if (greaterThanCondition.orEqualTo()) {
      return field.greaterOrEqual(valueField(greaterThanCondition.value(), greaterThanCondition, entityType));
    }
    return field.greaterThan(valueField(greaterThanCondition.value(), greaterThanCondition, entityType));
  }

  private static Condition handleLessThan(LessThanCondition lessThanCondition, EntityType entityType, Field<Object> field) {
    if (isDateCondition(lessThanCondition, entityType)) {
      return handleDate(lessThanCondition, field);
    }
    if (lessThanCondition.orEqualTo()) {
      return field.lessOrEqual(valueField(lessThanCondition.value(), lessThanCondition, entityType));
    }
    return field.lessThan(valueField(lessThanCondition.value(), lessThanCondition, entityType));
  }

  private static Condition handleAnd(AndCondition andCondition, EntityType entityType) {
    return and(andCondition.value().stream().map(c -> getSqlCondition(c, entityType)).toList());
  }

  private static Condition handleRegEx(RegexCondition regexCondition, Field<Object> field) {
    // perform case-insensitive regex search
    return condition("{0} ~* {1}", field, val(regexCondition.value()));
  }

  private static Condition handleContains(ContainsCondition containsCondition, EntityType entityType, Field<Object> field) {
    if (containsCondition.value() instanceof String value) {
      // Casting required to use ARRAY_CONTAINS operator
      return cast(field, String[].class).contains(valueField(new String[]{value.toLowerCase()}, containsCondition, entityType));
    }
    // Cast non-string types to string for easy comparison
    return cast(field, String[].class).contains(valueField(new String[]{containsCondition.value().toString()}, containsCondition, entityType));
  }

  private static Condition handleNotContains(NotContainsCondition notContainsCondition, EntityType entityType, Field<Object> field) {
    if (notContainsCondition.value() instanceof String value) {
      // Casting required to use ARRAY_CONTAINS operator
      return cast(field, String[].class).notContains(valueField(new String[]{value.toLowerCase()}, notContainsCondition, entityType));
    }
    // Cast non-string types to string for easy comparison
    return cast(field, String[].class).notContains(valueField(new String[]{notContainsCondition.value().toString()}, notContainsCondition, entityType));
  }

  private static Condition handleEmpty(EmptyCondition emptyCondition, EntityType entityType, Field<Object> field) {
    String fieldType = getColumn(emptyCondition, entityType)
      .getDataType()
      .getDataType();
    boolean isEmpty = Boolean.TRUE.equals(emptyCondition.value());
    var nullCondition = isEmpty ? field.isNull() : field.isNotNull();
    return switch (fieldType) {
      case STRING_TYPE -> isEmpty ? nullCondition.or(field.eq("")) : nullCondition.and(field.ne(""));
      case "arrayType" -> {
        var cardinality = cardinality(DSL.cast(field, String[].class));
        yield isEmpty ? nullCondition.or(cardinality.eq(0)) :  nullCondition.and(cardinality.ne(0));
      }
      default -> nullCondition;
    };
  }

  /**
   * Create a case-insensitive comparison of the given fieldCondition with the given entityType.
   * <p /><b>Note: This method always converts the comparison value to lower-case, while the column will be converted
   * to lower-case only if it does not have a filterValueGetter (currently, we assume the filterValueGetter
   * will convert the column to lower-case. If that assumption changes, this method will need to be updated)</b>
   *
   * @param value                      The value side of the string comparison (i.e., the value being compared to the field)
   * @param toCaseInsensitiveCondition The function to build a Condition where the column is converted to lower-case
   * @param toCaseSensitiveCondition   The function to build a Condition where the column is *not* converted to lower-case
   */
  private static Condition caseInsensitiveComparison(FieldCondition<?> fieldCondition, EntityType entityType, Field<Object> field,
                                                     String value,
                                                     BiFunction<Field<Object>, Field<String>, Condition> toCaseInsensitiveCondition,
                                                     BiFunction<Field<Object>, Field<Object>, Condition> toCaseSensitiveCondition) {
    EntityTypeColumn column = getColumn(fieldCondition, entityType);
    boolean shouldConvertColumnToLower = column.getFilterValueGetter() == null;

    return shouldConvertColumnToLower
      ? toCaseInsensitiveCondition.apply(field, valueField(value, fieldCondition, entityType))
      : toCaseSensitiveCondition.apply(field, valueField(value.toLowerCase(), fieldCondition, entityType));
  }

  private static Field<Object> field(FieldCondition<?> condition, EntityType entityType) {
    String columnName = condition.fieldName();
    return entityType.getColumns()
      .stream()
      .filter(col -> columnName.equals(col.getName()))
      .findFirst()
      .map(SqlFieldIdentificationUtils::getSqlFilterField)
      .orElseThrow(() -> new ColumnNotFoundException(entityType.getName(), columnName));
  }

  private static String getColumnDataType(EntityType entityType, FieldCondition<?> fieldCondition) {
    return entityType.getColumns()
      .stream()
      .filter(col -> fieldCondition.fieldName().equals(col.getName()))
      .map(col -> col.getDataType().getDataType())
      .findFirst()
      .orElseThrow(() -> new ColumnNotFoundException(entityType.getName(), fieldCondition.fieldName()));
  }

  // Suppress the unchecked cast warning on the Class<T> cast below. We need the correct type there in order to get
  // a reasonable level of type-safety with the return type on this method
  @SuppressWarnings("unchecked")
  private static <T> Field<T> valueField(T value, FieldCondition<?> condition, EntityType entityType) {
    EntityTypeColumn column = getColumn(condition, entityType);
    if (column.getValueFunction() != null) {
      return DSL.field(column.getValueFunction(), (Class<T>) value.getClass(), param("value", value));
    }
    return val(value);
  }
}
