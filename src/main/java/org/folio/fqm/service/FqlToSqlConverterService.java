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
  private final Map<Class<? extends FqlCondition<?>>, BiFunction<FqlCondition<?>, EntityType, Condition>> sqlConverters = Map.of(
    EqualsCondition.class, (cnd, ent) -> handleEquals((EqualsCondition) cnd, ent),
    NotEqualsCondition.class, (cnd, ent) -> handleNotEquals((NotEqualsCondition) cnd, ent),
    InCondition.class, (cnd, ent) -> handleIn((InCondition) cnd, ent),
    NotInCondition.class, (cnd, ent) -> handleNotIn((NotInCondition) cnd, ent),
    GreaterThanCondition.class, (cnd, ent) -> handleGreaterThan((GreaterThanCondition) cnd, ent),
    LessThanCondition.class, (cnd, ent) -> handleLessThan((LessThanCondition) cnd, ent),
    AndCondition.class, (cnd, ent) -> handleAnd((AndCondition) cnd, ent),
    RegexCondition.class, (cnd, ent) -> handleRegEx((RegexCondition) cnd, ent),
    ContainsCondition.class, (cnd, ent) -> handleContains((ContainsCondition) cnd, ent),
    NotContainsCondition.class, (cnd, ent) -> handleNotContains((NotContainsCondition) cnd, ent)
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
  public Condition getSqlCondition(FqlCondition<?> fqlCondition, EntityType entityType) {
    return sqlConverters.getOrDefault(fqlCondition.getClass(), (c, e) -> falseCondition()).apply(fqlCondition, entityType);
  }

  private Condition handleEquals(EqualsCondition equalsCondition, EntityType entityType) {
    if (isDateCondition(equalsCondition, entityType)) {
      return handleDate(equalsCondition, entityType);
    }
    if (equalsCondition.value() instanceof String s) {
      return field(equalsCondition, entityType).equalIgnoreCase(s);
    }
    return field(equalsCondition, entityType).eq(equalsCondition.value());
  }

  private Condition handleNotEquals(NotEqualsCondition notEqualsCondition, EntityType entityType) {
    if (isDateCondition(notEqualsCondition, entityType)) {
      return handleDate(notEqualsCondition, entityType);
    }
    if (notEqualsCondition.value() instanceof String s) {
      return field(notEqualsCondition, entityType).notEqualIgnoreCase(s);
    }
    return field(notEqualsCondition, entityType).ne(notEqualsCondition.value());
  }

  private Condition handleDate(FieldCondition<?> fieldCondition, EntityType entityType) {
    Condition condition = DSL.falseCondition();
    var dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
    var date = LocalDate.parse((String) fieldCondition.value(), dateTimeFormatter);
    LocalDate nextDay = date.plusDays(1);
    if (fieldCondition instanceof EqualsCondition equalsCondition) {
      condition = field(equalsCondition, entityType)
        .greaterOrEqual(date.toString())
        .and(field(equalsCondition, entityType).lessThan(nextDay.toString()));
    } else if (fieldCondition instanceof NotEqualsCondition notEqualsCondition) {
      condition = field(notEqualsCondition, entityType)
        .greaterOrEqual(nextDay.toString())
        .or(field(notEqualsCondition, entityType).lessThan(date.toString()));
    } else if (fieldCondition instanceof GreaterThanCondition greaterThanCondition) {
      if (greaterThanCondition.orEqualTo()) {
        condition = field(greaterThanCondition, entityType)
          .greaterOrEqual(date.toString());
      } else {
        condition = field(greaterThanCondition, entityType)
          .greaterOrEqual(nextDay.toString());
      }
    } else if (fieldCondition instanceof LessThanCondition lessThanCondition) {
      if (lessThanCondition.orEqualTo()) {
        condition = field(lessThanCondition, entityType)
          .lessThan(nextDay.toString());
      } else {
        condition = field(lessThanCondition, entityType)
          .lessThan(date.toString());
      }
    }
    return condition;
  }

  private boolean isDateCondition(FieldCondition<?> fieldCondition, EntityType entityType) {
    EntityDataType dataType = entityType.getColumns()
      .stream()
      .filter(col -> col.getName().equals(fieldCondition.fieldName()))
      .map(EntityTypeColumn::getDataType)
      .findFirst()
      .orElseThrow(() -> new ColumnNotFoundException(entityType.getName(), fieldCondition.fieldName()));
    return dataType instanceof DateType
      && ((String) fieldCondition.value()).matches(DATE_REGEX);
  }

  private Condition handleIn(InCondition inCondition, EntityType entityType) {
    List<Condition> conditionList = inCondition
      .value().stream()
      .map((Object val) -> {
        if (val instanceof String string) {
          return field(inCondition, entityType).equalIgnoreCase(string);
        } else {
          return field(inCondition, entityType).eq(val);
        }
      })
      .toList();

    return or(conditionList);
  }

  private Condition handleNotIn(NotInCondition notInCondition, EntityType entityType) {
    List<Condition> conditionList = notInCondition
      .value().stream()
      .map(val -> {
        if (val instanceof String string) {
          return field(notInCondition, entityType).notEqualIgnoreCase(string);
        } else {
          return field(notInCondition, entityType).notEqual(val);
        }
      })
      .toList();
    return and(conditionList);
  }

  private Condition handleGreaterThan(GreaterThanCondition greaterThanCondition, EntityType entityType) {
    if (isDateCondition(greaterThanCondition, entityType)) {
      return handleDate(greaterThanCondition, entityType);
    }
    if (greaterThanCondition.orEqualTo()) {
      return field(greaterThanCondition, entityType).greaterOrEqual(greaterThanCondition.value());
    }
    return field(greaterThanCondition, entityType).greaterThan(greaterThanCondition.value());
  }

  private Condition handleLessThan(LessThanCondition lessThanCondition, EntityType entityType) {
    if (isDateCondition(lessThanCondition, entityType)) {
      return handleDate(lessThanCondition, entityType);
    }
    if (lessThanCondition.orEqualTo()) {
      return field(lessThanCondition, entityType).lessOrEqual(lessThanCondition.value());
    }
    return field(lessThanCondition, entityType).lessThan(lessThanCondition.value());
  }

  private Condition handleAnd(AndCondition andCondition, EntityType entityType) {
    return and(andCondition.value().stream().map(c -> getSqlCondition(c, entityType)).toList());
  }

  private Condition handleRegEx(RegexCondition regExCondition, EntityType entityType) {
    // perform case-insensitive regex search
    return condition("{0} ~* {1}", field(regExCondition, entityType), val(regExCondition.value()));
  }

  private Condition handleContains(ContainsCondition containsCondition, EntityType entityType) {
    if (containsCondition.value() instanceof String s) {
      return field(containsCondition, entityType).containsIgnoreCase(s);
    }
    return field(containsCondition, entityType).contains(containsCondition.value());
  }

  private Condition handleNotContains(NotContainsCondition notContainsCondition, EntityType entityType) {
    Field<Object> field = field(notContainsCondition, entityType);
    if (notContainsCondition.value() instanceof String s) {
      return field.notContainsIgnoreCase(s).or(field(notContainsCondition, entityType).isNull());
    }
    return field.notContains(notContainsCondition.value()).or(field(notContainsCondition, entityType).isNull());
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
}
