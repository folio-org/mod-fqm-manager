package org.folio.fqm.service;

import org.folio.fql.model.AndCondition;
import org.folio.fql.model.EmptyCondition;
import org.folio.fql.model.EqualsCondition;
import org.folio.fql.model.FieldCondition;
import org.folio.fql.model.FqlCondition;
import org.folio.fql.model.GreaterThanCondition;
import org.folio.fql.model.InCondition;
import org.folio.fql.model.LessThanCondition;
import org.folio.fql.model.NotEqualsCondition;
import org.folio.fql.model.NotInCondition;
import org.folio.fql.model.RegexCondition;
import org.folio.fql.model.StartsWithCondition;
import org.folio.fql.model.ContainsCondition;
import org.folio.fql.service.FqlService;
import org.folio.fql.service.FqlValidationService;
import org.folio.fqm.exception.FieldNotFoundException;
import org.folio.fqm.exception.InvalidFqlException;
import org.folio.fqm.utils.SqlFieldIdentificationUtils;
import org.folio.querytool.domain.dto.DateTimeType;
import org.folio.querytool.domain.dto.EntityDataType;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.Field;
import org.jooq.Condition;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
  public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneId.of("UTC"));
  private static final String DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$";
  private static final String DATE_TIME_REGEX = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}$";
  private static final String STRING_TYPE = "stringType";
  private static final String RANGED_UUID_TYPE = "rangedUUIDType";
  private static final String STRING_UUID_TYPE = "stringUUIDType";
  private static final String OPEN_UUID_TYPE = "openUUIDType";
  private static final String ARRAY_TYPE = "arrayType";
  private static final String JSONB_ARRAY_TYPE = "jsonbArrayType";
  private static final String DATE_TYPE = "dateType";
  private static final int INLINE_STRING_CHARACTER_LIMIT = 32;
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
    if (fqlCondition instanceof FieldCondition<?> fieldCondition) {
      final org.jooq.Field<Object> field = field(fieldCondition, entityType);
      return switch (fqlCondition.getClass().getSimpleName()) {
        case "EqualsCondition" -> handleEquals((EqualsCondition) fqlCondition, entityType, field);
        case "NotEqualsCondition" -> handleNotEquals((NotEqualsCondition) fqlCondition, entityType, field);
        case "InCondition" -> handleIn((InCondition) fqlCondition, entityType, field);
        case "NotInCondition" -> handleNotIn((NotInCondition) fqlCondition, entityType, field);
        case "GreaterThanCondition" -> handleGreaterThan((GreaterThanCondition) fqlCondition, entityType, field);
        case "LessThanCondition" -> handleLessThan((LessThanCondition) fqlCondition, entityType, field);
        case "RegexCondition" -> handleRegEx((RegexCondition) fqlCondition, entityType, field);
        case "StartsWithCondition" -> handleStartsWith((StartsWithCondition) fqlCondition, entityType, field);
        case "ContainsCondition" -> handleContains((ContainsCondition) fqlCondition, entityType, field);
        case "EmptyCondition" -> handleEmpty((EmptyCondition) fqlCondition, entityType, field);
        default -> falseCondition();
      };
    } else {
      return switch (fqlCondition.getClass().getSimpleName()) {
        case "AndCondition" -> handleAnd((AndCondition) fqlCondition, entityType);
        default -> falseCondition();
      };
    }
  }

  private static Condition handleEquals(EqualsCondition equalsCondition, EntityType entityType, org.jooq.Field<Object> field) {
    if (isDateTimeCondition(equalsCondition, entityType)) {
      return handleDateTime(equalsCondition, field, entityType);
    }
    String dataType = getFieldDataType(entityType, equalsCondition);
    if (ARRAY_TYPE.equals(dataType)) {
      var valueArray = cast(array(valueField(equalsCondition.value(), equalsCondition, entityType)), String[].class);
      return arrayOverlap(cast(field, String[].class), valueArray);
    }
    if (JSONB_ARRAY_TYPE.equals(dataType)) {
      Field entityField = getField(equalsCondition, entityType);
      if (entityField.getValueFunction() != null) {
        var transformedValue = valueField(equalsCondition.value(), equalsCondition, entityType);
        return DSL.condition("{0} @> jsonb_build_array({1}::text)", field.cast(JSONB.class), transformedValue);
      } else {
        var jsonbValue = DSL.inline("[\"" + equalsCondition.value() + "\"]");
        return DSL.condition("{0} @> {1}::jsonb", field.cast(JSONB.class), jsonbValue);
      }
    }
    String filterFieldDataType = getFieldForFiltering(equalsCondition, entityType).getDataType().getDataType();
    if (RANGED_UUID_TYPE.equals(filterFieldDataType) || OPEN_UUID_TYPE.equals(filterFieldDataType)) {
      String value = (String) equalsCondition.value();
      return cast(field, UUID.class).eq(cast(value, UUID.class));
    }
    if (STRING_TYPE.equals(dataType) || STRING_UUID_TYPE.equals(dataType)) {
      return caseInsensitiveComparison(equalsCondition, entityType, field, (String) equalsCondition.value(), org.jooq.Field::equalIgnoreCase, org.jooq.Field::eq);
    }
    return field.eq(valueField(equalsCondition.value(), equalsCondition, entityType));
  }

  private static Condition handleNotEquals(NotEqualsCondition notEqualsCondition, EntityType entityType, org.jooq.Field<Object> field) {
    String dataType = getFieldDataType(entityType, notEqualsCondition);
    String filterFieldDataType = getFieldForFiltering(notEqualsCondition, entityType).getDataType().getDataType();
    Condition baseCondition;
    if (isDateTimeCondition(notEqualsCondition, entityType)) {
      baseCondition = handleDateTime(notEqualsCondition, field, entityType);
    } else if (ARRAY_TYPE.equals(dataType)) {
      var valueArray = cast(array(valueField(notEqualsCondition.value(), notEqualsCondition, entityType)), String[].class);
      baseCondition = not(arrayOverlap(cast(field, String[].class), valueArray));
    } else if (JSONB_ARRAY_TYPE.equals(dataType)) {
      Field entityField = getField(notEqualsCondition, entityType);
      if (entityField.getValueFunction() != null) {
        var transformedValue = valueField(notEqualsCondition.value(), notEqualsCondition, entityType);
        baseCondition = DSL.condition("NOT({0} @> jsonb_build_array({1}::text))", field.cast(JSONB.class), transformedValue);
      } else {
        var jsonbValue = DSL.inline("[\"" + notEqualsCondition.value() + "\"]");
        baseCondition = DSL.condition("NOT({0} @> {1}::jsonb)", field.cast(JSONB.class), jsonbValue);
      }
    } else if (RANGED_UUID_TYPE.equals(filterFieldDataType) || OPEN_UUID_TYPE.equals(filterFieldDataType)) {
      try {
        UUID uuidValue = UUID.fromString((String) notEqualsCondition.value());
        baseCondition = cast(field, UUID.class).ne(cast(uuidValue, UUID.class));
      } catch (IllegalArgumentException e) {
        baseCondition = trueCondition();
      }
    } else if (STRING_TYPE.equals(dataType) || STRING_UUID_TYPE.equals(dataType)) {
      baseCondition = caseInsensitiveComparison(
        notEqualsCondition,
        entityType,
        field,
        (String) notEqualsCondition.value(),
        org.jooq.Field::notEqualIgnoreCase,
        org.jooq.Field::ne
      );
    } else {
      baseCondition = field.ne(valueField(notEqualsCondition.value(), notEqualsCondition, entityType));
    }
    return baseCondition.or(field.isNull());
  }

  private static Condition handleDateTime(FieldCondition<?> fieldCondition, org.jooq.Field<Object> field, EntityType entityType) {
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
      condition = field.greaterOrEqual(valueField(dateTime.format(DATE_TIME_FORMATTER), fieldCondition, entityType))
        .and(field.lessThan(valueField(nextDayDateTime.format(DATE_TIME_FORMATTER), fieldCondition, entityType)));
    } else if (fieldCondition instanceof NotEqualsCondition) {
      condition = field.greaterOrEqual(valueField(nextDayDateTime.format(DATE_TIME_FORMATTER), fieldCondition, entityType))
        .or(field.lessThan(valueField(dateTime.format(DATE_TIME_FORMATTER), fieldCondition, entityType)));
    } else if (fieldCondition instanceof GreaterThanCondition greaterThanCondition) {
      if (greaterThanCondition.orEqualTo()) {
        condition = field.greaterOrEqual(valueField(dateTime.format(DATE_TIME_FORMATTER), fieldCondition, entityType));
      } else {
        condition = field.greaterOrEqual(valueField(nextDayDateTime.format(DATE_TIME_FORMATTER), fieldCondition, entityType));
      }
    } else if (fieldCondition instanceof LessThanCondition lessThanCondition) {
      if (lessThanCondition.orEqualTo()) {
        condition = field.lessThan(valueField(nextDayDateTime.format(DATE_TIME_FORMATTER), fieldCondition, entityType));
      } else {
        condition = field.lessThan(valueField(dateTime.format(DATE_TIME_FORMATTER), fieldCondition, entityType));
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

  private static boolean isDateTimeCondition(FieldCondition<?> fieldCondition, EntityType entityType) {
    EntityDataType dataType = getFieldForFiltering(fieldCondition, entityType).getDataType();
    return dataType instanceof DateTimeType;
  }

  private static Condition handleIn(InCondition inCondition, EntityType entityType, org.jooq.Field<Object> field) {
    String dataType = getFieldDataType(entityType, inCondition);

    if (ARRAY_TYPE.equals(dataType)) {
      var valueList = inCondition
        .value().stream()
        .map(val -> valueField(val, inCondition, entityType))
        .toArray(org.jooq.Field[]::new);
      var valueArray = cast(array(valueList), String[].class);
      return arrayOverlap(cast(field, String[].class), valueArray);
    }

    if (JSONB_ARRAY_TYPE.equals(dataType)) {
      Field entityField = getField(inCondition, entityType);
      List<Condition> conditionList = inCondition
        .value().stream()
        .map(val -> {
          if (entityField.getValueFunction() != null) {
            var transformedValue = valueField(val, inCondition, entityType);
            return DSL.condition("{0} @> jsonb_build_array({1}::text)", field.cast(JSONB.class), transformedValue);
          } else {
            var jsonbValue = DSL.inline("[\"" + val + "\"]");
            return DSL.condition("{0} @> {1}::jsonb", field.cast(JSONB.class), jsonbValue);
          }
        })
        .toList();
      return or(conditionList);
    }

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
    String dataType = getFieldDataType(entityType, notInCondition);

    if (ARRAY_TYPE.equals(dataType)) {
      List<Condition> conditionList = notInCondition
        .value().stream()
        .map(val -> {
          var valueArray = cast(array(valueField(val, notInCondition, entityType)), String[].class);
          return not(arrayOverlap(cast(field, String[].class), valueArray));
        })
        .toList();
      return field.isNull().or(and(conditionList));
    }

    if (JSONB_ARRAY_TYPE.equals(dataType)) {
      Field entityField = getField(notInCondition, entityType);
      List<Condition> conditionList = notInCondition
        .value().stream()
        .map(val -> {
          if (entityField.getValueFunction() != null) {
            var transformedValue = valueField(val, notInCondition, entityType);
            return DSL.condition("NOT({0} @> jsonb_build_array({1}::text))", field.cast(JSONB.class), transformedValue);
          } else {
            var jsonbValue = DSL.inline("[\"" + val + "\"]");
            return DSL.condition("NOT({0} @> {1}::jsonb)", field.cast(JSONB.class), jsonbValue);
          }
        })
        .toList();
      return field.isNull().or(and(conditionList));
    }

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
    return field.isNull().or(and(conditionList));
  }

  private static Condition handleGreaterThan(GreaterThanCondition greaterThanCondition, EntityType entityType, org.jooq.Field<Object> field) {
    if (isDateTimeCondition(greaterThanCondition, entityType)) {
      return handleDateTime(greaterThanCondition, field, entityType);
    }
    if (greaterThanCondition.orEqualTo()) {
      return field.greaterOrEqual(valueField(greaterThanCondition.value(), greaterThanCondition, entityType));
    }
    return field.greaterThan(valueField(greaterThanCondition.value(), greaterThanCondition, entityType));
  }

  private static Condition handleLessThan(LessThanCondition lessThanCondition, EntityType entityType, org.jooq.Field<Object> field) {
    if (isDateTimeCondition(lessThanCondition, entityType)) {
      return handleDateTime(lessThanCondition, field, entityType);
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
    String dataType = getFieldDataType(entityType, regexCondition);
    if (ARRAY_TYPE.equals(dataType)) {
      return condition("exists (select 1 from unnest({0}) where unnest ~* {1})", field, valueField(regexCondition.value(), regexCondition, entityType));
    }
    if (JSONB_ARRAY_TYPE.equals(dataType)) {
      return condition("exists (select 1 from jsonb_array_elements_text({0}) as elem where elem ~* {1})", field.cast(JSONB.class), valueField(regexCondition.value(), regexCondition, entityType));
    }
    return condition("{0} ~* {1}", field, valueField(regexCondition.value(), regexCondition, entityType));
  }

  private static Condition handleContains(ContainsCondition containsCondition, EntityType entityType, org.jooq.Field<Object> field) {
    return field.contains(valueField(containsCondition.value(), containsCondition, entityType));
  }

  private static Condition handleStartsWith(StartsWithCondition startsWithCondition, EntityType entityType, org.jooq.Field<Object> field) {
    String dataType = getFieldDataType(entityType, startsWithCondition);
    if (ARRAY_TYPE.equals(dataType)) {
      return condition("exists (select 1 from unnest({0}) where unnest like {1})", field, DSL.concat(valueField(startsWithCondition.value(), startsWithCondition, entityType), DSL.inline("%")));
    }
    if (JSONB_ARRAY_TYPE.equals(dataType)) {
      return condition("exists (select 1 from jsonb_array_elements_text({0}) as elem where elem like {1})", field.cast(JSONB.class), DSL.concat(valueField(startsWithCondition.value(), startsWithCondition, entityType), DSL.inline("%")));
    }
    return field.startsWith(valueField(startsWithCondition.value(), startsWithCondition, entityType));
  }

  private static Condition handleEmpty(EmptyCondition emptyCondition, EntityType entityType, org.jooq.Field<Object> field) {
    String fieldType = getFieldDataType(entityType, emptyCondition);
    boolean isEmpty = Boolean.TRUE.equals(emptyCondition.value());
    var nullCondition = isEmpty ? field.isNull() : field.isNotNull();

    return switch (fieldType) {
      case STRING_TYPE ->
        isEmpty ? nullCondition.or(cast(field, String.class).eq("")) : nullCondition.and(cast(field, String.class).ne(""));
      case ARRAY_TYPE -> {
        var cardinality = cardinality(cast(field, String[].class));
        if (isEmpty) {
          yield nullCondition.or(cardinality.eq(0)).or(ALL_NULLS.formatted(field));
        } else {
          yield nullCondition.and(cardinality.ne(0)).and(NOT_ALL_NULLS.formatted(field));
        }
      }
      case JSONB_ARRAY_TYPE -> {
        var jsonbCardinality = DSL.field("jsonb_array_length({0})", Integer.class, field);
        if (isEmpty) {
          yield nullCondition.or(jsonbCardinality.eq(0));
        } else {
          yield nullCondition.and(jsonbCardinality.ne(0));
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
    boolean inline = shouldInlineValue(entityType, condition, value);

    // ensure that UUIDs are cast properly; this also ensures that invalid UUIDs will be gracefully null'd
    // inside array fields (matching behavior for non-array fields)
    String filterFieldDataType = getFieldForFiltering(condition, entityType).getDataType().getDataType();
    if (RANGED_UUID_TYPE.equals(filterFieldDataType) || OPEN_UUID_TYPE.equals(filterFieldDataType)) {
      return (org.jooq.Field<T>) cast(value, UUID.class);
    }

    if (field.getValueFunction() != null) {
      if (inline) {
        return DSL.field(field.getValueFunction(), (Class<T>) value.getClass(), DSL.inline(value));
      } else {
        return DSL.field(field.getValueFunction(), (Class<T>) value.getClass(), DSL.param("value", value));
      }
    }
    return inline ? DSL.inline(value) : DSL.val(value);
  }

  // Determine whether the value should be inlined in the generated SQL. Inlining certain values can provide a
  // performance benefit without much drawback.
  private static <T> boolean shouldInlineValue(EntityType entityType, FieldCondition<?> condition, T value) {
    var dataType = getFieldDataType(entityType, condition);
    switch (dataType) {
      case STRING_TYPE:
        // inline small strings only, due to potential performance hit for large strings
        return value != null && value.toString().length() < INLINE_STRING_CHARACTER_LIMIT;

      case OPEN_UUID_TYPE,
           RANGED_UUID_TYPE,
           STRING_UUID_TYPE,
           DATE_TYPE,
           "enumType",
           "booleanType",
           "integerType",
           "numberType":
        return true;

      // Don't inline arrays or objects since there is very little benefit to doing so
      case ARRAY_TYPE,
           JSONB_ARRAY_TYPE,
           "objectType":
      default:
        return false;
    }
  }
}
