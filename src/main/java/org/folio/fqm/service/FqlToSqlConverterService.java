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
import java.util.Map;
import java.util.function.BiFunction;

import static org.jooq.impl.DSL.and;
import static org.jooq.impl.DSL.condition;
import static org.jooq.impl.DSL.falseCondition;
import static org.jooq.impl.DSL.or;
import static org.jooq.impl.DSL.val;

/**
 * Class responsible for converting an FQL query to SQL query
 */
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class FqlToSqlConverterService {

  // The compiler is unable to determine handle a type parameter on ConditionHandler
  // (depending on how it's constrained, it either fails to compile this initialization or uses of the variable)
  // IDEs seem to be able to handle ConditionHandler
  @SuppressWarnings("rawtypes")
  private static final Map<Class<? extends FqlCondition<?>>, ConditionHandler> sqlConverters = Map.ofEntries(
    buildMapping(EqualsCondition.class, FqlToSqlConverterService::handleEquals),
    buildMapping(NotEqualsCondition.class, FqlToSqlConverterService::handleNotEquals),
    buildMapping(InCondition.class, FqlToSqlConverterService::handleIn),
    buildMapping(NotInCondition.class, FqlToSqlConverterService::handleNotIn),
    buildMapping(GreaterThanCondition.class, FqlToSqlConverterService::handleGreaterThan),
    buildMapping(LessThanCondition.class, FqlToSqlConverterService::handleLessThan),
    buildMapping(AndCondition.class, FqlToSqlConverterService::handleAnd),
    buildMapping(RegexCondition.class, FqlToSqlConverterService::handleRegEx),
    buildMapping(ContainsCondition.class, FqlToSqlConverterService::handleContains),
    buildMapping(NotContainsCondition.class, FqlToSqlConverterService::handleNotContains)
  );

  /**
   * This regex matches the ISO_LOCAL_DATE format. Required: yyyy-MM-dd.
   * If datetime is provided in this form, a date-wise comparison will be performed. In other words,
   * any record matching the provided date will be retrieved. If datetime is provided in any other form,
   * an exact comparison will be performed.
   */
  private static final String DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$";

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
  // The unchecked warning is suppressed because sqlConverters has to have a raw type, forcing its use to have an unchecked call
  @SuppressWarnings("unchecked")
  public static Condition getSqlCondition(FqlCondition<?> fqlCondition, EntityType entityType) {
    final Field<Object> field = fqlCondition instanceof FieldCondition<?> fieldCondition
      ? field(fieldCondition, entityType)
      : null;
    return sqlConverters.getOrDefault(fqlCondition.getClass(), (condition, et, f) -> falseCondition()).handle(fqlCondition, entityType, field);
  }

  private static Condition handleEquals(EqualsCondition equalsCondition, EntityType entityType, Field<Object> field) {
    if (isDateCondition(equalsCondition, entityType)) {
      return handleDate(equalsCondition, field);
    }
    if (equalsCondition.value() instanceof String value) {
      return caseInsensitiveComparison(equalsCondition, entityType, field, value, Field::equalIgnoreCase, Field::eq);
    }
    return field.eq(equalsCondition.value());
  }

  private static Condition handleNotEquals(NotEqualsCondition notEqualsCondition, EntityType entityType, Field<Object> field) {
    if (isDateCondition(notEqualsCondition, entityType)) {
      return handleDate(notEqualsCondition, field);
    }
    if (notEqualsCondition.value() instanceof String value) {
      return caseInsensitiveComparison(notEqualsCondition, entityType, field, value, Field::notEqualIgnoreCase, Field::ne);
    }
    return field.ne(notEqualsCondition.value());
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
      .map((Object val) -> {
        if (val instanceof String value) {
          return caseInsensitiveComparison(inCondition, entityType, field, value, Field::equalIgnoreCase, Field::eq);
        } else {
          return field.eq(val);
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
          return field.notEqual(val);
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
      return field.greaterOrEqual(greaterThanCondition.value());
    }
    return field.greaterThan(greaterThanCondition.value());
  }

  private static Condition handleLessThan(LessThanCondition lessThanCondition, EntityType entityType, Field<Object> field) {
    if (isDateCondition(lessThanCondition, entityType)) {
      return handleDate(lessThanCondition, field);
    }
    if (lessThanCondition.orEqualTo()) {
      return field.lessOrEqual(lessThanCondition.value());
    }
    return field.lessThan(lessThanCondition.value());
  }

  private static Condition handleAnd(AndCondition andCondition, EntityType entityType, Field<Object> field) {
    return and(andCondition.value().stream().map(c -> getSqlCondition(c, entityType)).toList());
  }

  private static Condition handleRegEx(RegexCondition regexCondition, EntityType entityType, Field<Object> field) {
    // perform case-insensitive regex search
    return condition("{0} ~* {1}", field, val(regexCondition.value()));
  }

  private static Condition handleContains(ContainsCondition containsCondition, EntityType entityType, Field<Object> field) {
    if (containsCondition.value() instanceof String value) {
      // Casting required to use ARRAY_CONTAINS operator
      // Assumes usage of a filterValueGetter with lower() function
      return DSL.cast(field, String[].class).contains(new String[] {value.toLowerCase()});
    }
    // Cast non-string types to string for easy comparison
    return DSL.cast(field, String[].class).contains(new String[] {containsCondition.value().toString()});
  }

  private static Condition handleNotContains(NotContainsCondition notContainsCondition, EntityType entityType, Field<Object> field) {
    if (notContainsCondition.value() instanceof String value) {
      // Casting required to use ARRAY_CONTAINS operator
      // Assumes usage of a filterValueGetter with lower() function
      return DSL.cast(field, String[].class).notContains(new String[] {value.toLowerCase()});
    }
    // Cast non-string types to string for easy comparison
    return DSL.cast(field, String[].class).notContains(new String[] {notContainsCondition.value().toString()});
  }

  /**
   * Create a case-insensitive comparison of the given fieldCondition with the given entityType.
   * <p /><b>Note: This method always converts the comparison value to lower-case, while the column will be converted
   * to lower-case only if it does not have a filterValueGetter (currently, we assume the filterValueGetter
   * will convert the column to lower-case. If that assumption changes, this method will need to be updated)</b>
   *
   * @param value The value side of the string comparison (i.e., the value being compared to the field)
   * @param toCaseInsensitiveCondition The function to build a Condition where the column is converted to lower-case
   * @param toCaseSensitiveCondition   The function to build a Condition where the column is *not* converted to lower-case
   */
  private static Condition caseInsensitiveComparison(FieldCondition<?> fieldCondition, EntityType entityType, Field<Object> field,
                                                     String value,
                                                     BiFunction<Field<Object>, String, Condition> toCaseInsensitiveCondition,
                                                     BiFunction<Field<Object>, String, Condition> toCaseSensitiveCondition) {
    EntityTypeColumn column = getColumn(fieldCondition, entityType);
    boolean shouldConvertColumnToLower = column.getFilterValueGetter() == null;

    return shouldConvertColumnToLower
      ? toCaseInsensitiveCondition.apply(field, value)
      : toCaseSensitiveCondition.apply(field, value.toLowerCase());
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

  /**
   * Build a mapping from a condition class to a condition handler.
   * Note: This method exists in order to provide some type-safety around sqlConverters, to ensure the class and condition
   * handler are for matching implementations of FqlCondition, since we can provide more powerful type constraints on
   * methods than we can on an object
   */
  private static <T extends FqlCondition<?>> Map.Entry<Class<T>, ? extends ConditionHandler<T>> buildMapping(Class<T> conditionClass, ConditionHandler<T> f) {
    return Map.entry(conditionClass, f);
  }

  @FunctionalInterface
  private interface ConditionHandler<T extends FqlCondition<?>> {
    Condition handle(T fqlCondition, EntityType entityType, Field<Object> field);
  }
}
