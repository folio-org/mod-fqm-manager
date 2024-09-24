package org.folio.fqm.service;

import org.folio.fql.model.AndCondition;
import org.folio.fql.model.ContainsAllCondition;
import org.folio.fql.model.ContainsAnyCondition;
import org.folio.fql.model.EmptyCondition;
import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.FieldCondition;
import org.folio.fql.model.FqlCondition;
import org.folio.fql.model.GreaterThanCondition;
import org.folio.fql.model.InCondition;
import org.folio.fql.model.LessThanCondition;
import org.folio.fql.model.NotContainsAllCondition;
import org.folio.fql.model.NotContainsAnyCondition;
import org.folio.fql.model.NotEqualsCondition;
import org.folio.fql.model.NotInCondition;
import org.folio.fql.model.RegexCondition;
import org.folio.fql.service.FqlService;
import org.folio.fql.service.FqlValidationService;
import org.folio.fqm.exception.FieldNotFoundException;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.fqm.utils.SqlFieldIdentificationUtils;
import org.folio.querytool.domain.dto.DateType;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.Field;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.array;
import static org.jooq.impl.DSL.arrayOverlap;
import static org.jooq.impl.DSL.cast;
import static org.jooq.impl.DSL.cardinality;
import static org.jooq.impl.DSL.condition;
import static org.jooq.impl.DSL.falseCondition;
import static org.jooq.impl.DSL.not;
import static org.jooq.impl.DSL.or;
import static org.jooq.impl.DSL.param;
import static org.jooq.impl.DSL.val;
import static org.jooq.impl.DSL.trueCondition;

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
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
  private static final String DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$";
  private static final String DATE_TIME_REGEX = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}$";
  private static final String STRING_TYPE = "stringType";
  private static final String RANGED_UUID_TYPE = "rangedUUIDType";
  private static final String STRING_UUID_TYPE = "stringUUIDType";
  private static final String OPEN_UUID_TYPE = "openUUIDType";
  private static final String DATE_TYPE = "dateType";
  public static final String ALL_NULLS = """
    true = ALL(
      SELECT(
        unnest(
          cast(
            %s
            as varchar[]
          )
        )
      ) IS NULL
    )
    """;
  public static final String NOT_ALL_NULLS = """
    false = ANY(
      SELECT(
        unnest(
          cast(
            %s
            as varchar[]
          )
        )
      ) IS NULL
    )
    """;


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
    final org.jooq.Field<Object> field = fqlCondition instanceof FieldCondition<?> fieldCondition
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
      case "RegexCondition" -> handleRegEx((RegexCondition) fqlCondition, entityType, field);
      case "ContainsAllCondition" -> handleContainsAll((ContainsAllCondition) fqlCondition, entityType, field);
      case "NotContainsAllCondition" -> handleNotContainsAll((NotContainsAllCondition) fqlCondition, entityType, field);
      case "ContainsAnyCondition" -> handleContainsAny((ContainsAnyCondition) fqlCondition, entityType, field);
      case "NotContainsAnyCondition" -> handleNotContainsAny((NotContainsAnyCondition) fqlCondition, entityType, field);
      case "EmptyCondition" -> handleEmpty((EmptyCondition) fqlCondition, entityType, field);
      default -> falseCondition();
    };
  }

  private static Condition handleEquals(EqualsCondition equalsCondition, EntityType entityType, org.jooq.Field<Object> field) {
    if (isDateCondition(equalsCondition, entityType)) {
      return handleDate(equalsCondition, field);
    }
    String dataType = getFieldDataType(entityType, equalsCondition);
    String filterFieldDataType = getFieldForFiltering(equalsCondition, entityType).getDataType().getDataType();
    if (RANGED_UUID_TYPE.equals(filterFieldDataType) || OPEN_UUID_TYPE.equals(filterFieldDataType)) {
      String value = (String) equalsCondition.value();
      return cast(field, UUID.class).eq(cast(value, UUID.class));
    }
    if (STRING_TYPE.equals(dataType) || DATE_TYPE.equals(dataType) || STRING_UUID_TYPE.equals(dataType)) {
      return caseInsensitiveComparison(equalsCondition, entityType, field, (String) equalsCondition.value(), org.jooq.Field::equalIgnoreCase, org.jooq.Field::eq);
    }
    return field.eq(valueField(equalsCondition.value(), equalsCondition, entityType));
  }

  private static Condition handleNotEquals(NotEqualsCondition notEqualsCondition, EntityType entityType, org.jooq.Field<Object> field) {
    if (isDateCondition(notEqualsCondition, entityType)) {
      return handleDate(notEqualsCondition, field);
    }
    String dataType = getFieldDataType(entityType, notEqualsCondition);
    String filterFieldDataType = getFieldForFiltering(notEqualsCondition, entityType).getDataType().getDataType();
    if (RANGED_UUID_TYPE.equals(filterFieldDataType) || OPEN_UUID_TYPE.equals(filterFieldDataType)) {
      try {
        UUID uuidValue = UUID.fromString((String) notEqualsCondition.value());
        return cast(field, UUID.class).ne(cast(uuidValue, UUID.class));
      } catch (IllegalArgumentException e) {
        return trueCondition();
      }
    }
    if (STRING_TYPE.equals(dataType) || DATE_TYPE.equals(dataType) || STRING_UUID_TYPE.equals(dataType)) {
      return caseInsensitiveComparison(notEqualsCondition, entityType, field, (String) notEqualsCondition.value(), org.jooq.Field::notEqualIgnoreCase, org.jooq.Field::ne);
    }
    return field.ne(valueField(notEqualsCondition.value(), notEqualsCondition, entityType));
  }

  private static Condition handleDate(FieldCondition<?> fieldCondition, org.jooq.Field<Object> field) {
    String dateString = (String) fieldCondition.value();
    Condition condition = falseCondition();
    LocalDateTime dateTime;

    if (dateString.matches(DATE_REGEX)) {
      dateTime = LocalDate.parse(dateString, DATE_FORMATTER).atStartOfDay();
    } else if (dateString.matches(DATE_TIME_REGEX)) {
      dateTime = LocalDateTime.parse(dateString, DATE_TIME_FORMATTER);
    } else {
      throw new InvalidFqlException(fieldCondition.toString(), Map.of(fieldCondition.field().toString(), dateString));
    }

    LocalDateTime nextDayDateTime = dateTime.plusDays(1);
    if (fieldCondition instanceof EqualsCondition) {
      condition = field.greaterOrEqual(dateTime.format(DATE_TIME_FORMATTER))
        .and(field.lessThan(nextDayDateTime.format(DATE_TIME_FORMATTER)));
    } else if (fieldCondition instanceof NotEqualsCondition) {
      condition = field.greaterOrEqual(nextDayDateTime.format(DATE_TIME_FORMATTER))
        .or(field.lessThan(dateTime.format(DATE_TIME_FORMATTER)));
    } else if (fieldCondition instanceof GreaterThanCondition greaterThanCondition) {
      if (greaterThanCondition.orEqualTo()) {
        condition = field.greaterOrEqual(dateTime.format(DATE_TIME_FORMATTER));
      } else {
        condition = field.greaterOrEqual(nextDayDateTime.format(DATE_TIME_FORMATTER));
      }
    } else if (fieldCondition instanceof LessThanCondition lessThanCondition) {
      if (lessThanCondition.orEqualTo()) {
        condition = field.lessThan(nextDayDateTime.format(DATE_TIME_FORMATTER));
      } else {
        condition = field.lessThan(dateTime.format(DATE_TIME_FORMATTER));
      }
    }
    return condition;
  }

  private static Field getField(FieldCondition<?> fieldCondition, EntityType entityType) {
    return FqlValidationService
      .findFieldDefinition(fieldCondition.field(), entityType)
      .orElseThrow(() -> new FieldNotFoundException(entityType.getName(), fieldCondition.field()));
  }

  private static Field getFieldForFiltering(FieldCondition<?> fieldCondition, EntityType entityType) {
    return FqlValidationService
      .findFieldDefinitionForQuerying(fieldCondition.field(), entityType)
      .orElseThrow(() -> new FieldNotFoundException(entityType.getName(), fieldCondition.field()));
  }

  private static boolean isDateCondition(FieldCondition<?> fieldCondition, EntityType entityType) {
    EntityDataType dataType = getFieldForFiltering(fieldCondition, entityType).getDataType();
    return dataType instanceof DateType;
  }

  private static Condition handleIn(InCondition inCondition, EntityType entityType, org.jooq.Field<Object> field) {
    List<Condition> conditionList = inCondition.value().stream()
      .map(val -> {
        String filterFieldDataType = getFieldForFiltering(inCondition, entityType).getDataType().getDataType();
        if (RANGED_UUID_TYPE.equals(filterFieldDataType) || OPEN_UUID_TYPE.equals(filterFieldDataType)) {
          if (val instanceof String value) {
            return cast(field, UUID.class).eq(cast(value, UUID.class));
          }
          return field.eq(val);
        }
        if (val instanceof String value) {
          return caseInsensitiveComparison(inCondition, entityType, field, value, org.jooq.Field::equalIgnoreCase, org.jooq.Field::eq);
        }
        return field.eq(valueField(val, inCondition, entityType));
      })
      .toList();
    return or(conditionList);
  }

  private static Condition handleNotIn(NotInCondition notInCondition, EntityType entityType, org.jooq.Field<Object> field) {
    List<Condition> conditionList = notInCondition
      .value().stream()
      .map(val -> {
        String filterFieldDataType = getFieldForFiltering(notInCondition, entityType).getDataType().getDataType();
        if (RANGED_UUID_TYPE.equals(filterFieldDataType) || OPEN_UUID_TYPE.equals(filterFieldDataType)) {
          if (val instanceof String value) {
            try {
              UUID uuidValue = UUID.fromString(value);
              return cast(field, UUID.class).ne(cast(uuidValue, UUID.class));
            } catch (IllegalArgumentException e) {
              return trueCondition();
            }
          }
          return field.notEqual(val);
        }
        if (val instanceof String value) {
          return caseInsensitiveComparison(notInCondition, entityType, field, value, org.jooq.Field::notEqualIgnoreCase, org.jooq.Field::notEqual);
        }
        return field.notEqual(valueField(val, notInCondition, entityType));
      })
      .toList();
    return and(conditionList);
  }

  private static Condition handleGreaterThan(GreaterThanCondition greaterThanCondition, EntityType entityType, org.jooq.Field<Object> field) {
    if (isDateCondition(greaterThanCondition, entityType)) {
      return handleDate(greaterThanCondition, field);
    }
    if (greaterThanCondition.orEqualTo()) {
      return field.greaterOrEqual(valueField(greaterThanCondition.value(), greaterThanCondition, entityType));
    }
    return field.greaterThan(valueField(greaterThanCondition.value(), greaterThanCondition, entityType));
  }

  private static Condition handleLessThan(LessThanCondition lessThanCondition, EntityType entityType, org.jooq.Field<Object> field) {
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

  private static Condition handleRegEx(RegexCondition regexCondition, EntityType entityType, org.jooq.Field<Object> field) {
    // perform case-insensitive regex search
    return condition("{0} ~* {1}", field, valueField(regexCondition.value(), regexCondition, entityType));
  }

  private static Condition handleContainsAll(ContainsAllCondition containsAllCondition, EntityType entityType, org.jooq.Field<Object> field) {
    var valueList = containsAllCondition
      .value()
      .stream()
      .map(val -> valueField(val, containsAllCondition, entityType))
      .toArray(org.jooq.Field[]::new);
    var valueArray = cast(array(valueList), String[].class);
    return cast(field, String[].class).contains(valueArray);
  }


  private static Condition handleNotContainsAll(NotContainsAllCondition notContainsAllCondition, EntityType entityType, org.jooq.Field<Object> field) {
    var valueList = notContainsAllCondition
      .value()
      .stream()
      .map(val -> valueField(val, notContainsAllCondition, entityType))
      .toArray(org.jooq.Field[]::new);
    var valueArray = cast(array(valueList), String[].class);
    return cast(field, String[].class).notContains(valueArray);
  }

  private static Condition handleContainsAny(ContainsAnyCondition containsAnyCondition, EntityType entityType, org.jooq.Field<Object> field) {
    var valueList = containsAnyCondition
      .value()
      .stream()
      .map(val -> valueField(val, containsAnyCondition, entityType))
      .toArray(org.jooq.Field[]::new);
    var valueArray = cast(array(valueList), String[].class);
    return arrayOverlap(cast(field, String[].class), valueArray);
  }

  private static Condition handleNotContainsAny(NotContainsAnyCondition notContainsAnyCondition, EntityType entityType, org.jooq.Field<Object> field) {
    var valueList = notContainsAnyCondition
      .value()
      .stream()
      .map(val -> valueField(val, notContainsAnyCondition, entityType))
      .toArray(org.jooq.Field[]::new);
    var valueArray = cast(array(valueList), String[].class);
    return not(arrayOverlap(cast(field, String[].class), valueArray));
  }

  private static Condition handleEmpty(EmptyCondition emptyCondition, EntityType entityType, org.jooq.Field<Object> field) {
    String fieldType = getFieldDataType(entityType, emptyCondition);
    boolean isEmpty = Boolean.TRUE.equals(emptyCondition.value());
    var nullCondition = isEmpty ? field.isNull() : field.isNotNull();

    return switch (fieldType) {
      case STRING_TYPE ->
        isEmpty ? nullCondition.or(cast(field, String.class).eq("")) : nullCondition.and(cast(field, String.class).ne(""));
      case "arrayType" -> {
        var cardinality = cardinality(cast(field, String[].class));
        if (isEmpty) {
          yield nullCondition.or(cardinality.eq(0)).or(ALL_NULLS.formatted(field));
        } else {
          yield nullCondition.and(cardinality.ne(0)).and(NOT_ALL_NULLS.formatted(field));
        }
      }
      default -> nullCondition;
    };
  }

  /**
   * Create a case-insensitive comparison of the given fieldCondition with the given entityType.
   * <p /><b>Note: This method always converts the comparison value to lower-case, while the field will be converted
   * to lower-case only if it does not have a filterValueGetter (currently, we assume the filterValueGetter
   * will convert the field to lower-case. If that assumption changes, this method will need to be updated)</b>
   *
   * @param value                      The value side of the string comparison (i.e., the value being compared to the field)
   * @param toCaseInsensitiveCondition The function to build a Condition where the field is converted to lower-case
   * @param toCaseSensitiveCondition   The function to build a Condition where the field is *not* converted to lower-case
   */
  private static Condition caseInsensitiveComparison(FieldCondition<?> fieldCondition, EntityType entityType, org.jooq.Field<Object> field, String value,
                                                     BiFunction<org.jooq.Field<Object>, org.jooq.Field<String>, Condition> toCaseInsensitiveCondition,
                                                     BiFunction<org.jooq.Field<Object>, org.jooq.Field<Object>, Condition> toCaseSensitiveCondition) {
    Field entityField = getFieldForFiltering(fieldCondition, entityType);
    boolean shouldConvertColumnToLower = entityField.getFilterValueGetter() == null;

    return shouldConvertColumnToLower
      ? toCaseInsensitiveCondition.apply(field, valueField(value, fieldCondition, entityType))
      : toCaseSensitiveCondition.apply(field, valueField(value.toLowerCase(), fieldCondition, entityType));
  }

  private static org.jooq.Field<Object> field(FieldCondition<?> condition, EntityType entityType) {
    return SqlFieldIdentificationUtils.getSqlFilterField(getFieldForFiltering(condition, entityType));
  }

  private static String getFieldDataType(EntityType entityType, FieldCondition<?> fieldCondition) {
    return entityType.getColumns()
      .stream()
      .filter(col -> fieldCondition.field().getColumnName().equals(col.getName()))
      .map(col -> col.getDataType().getDataType())
      .findFirst()
      .orElseThrow(() -> new FieldNotFoundException(entityType.getName(), fieldCondition.field()));
  }

  // Suppress the unchecked cast warning on the Class<T> cast below. We need the correct type there in order to get
  // a reasonable level of type-safety with the return type on this method
  @SuppressWarnings("unchecked")
  private static <T> org.jooq.Field<T> valueField(T value, FieldCondition<?> condition, EntityType entityType) {
    Field field = getField(condition, entityType);
    if (field.getValueFunction() != null) {
      return DSL.field(field.getValueFunction(), (Class<T>) value.getClass(), param("value", value));
    }
    return val(value);
  }
}
