package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.jooq.DSLContext;
import org.jooq.Condition;
import org.jooq.Field;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.or;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.trueCondition;

@Repository
@RequiredArgsConstructor
@Log4j2
public class EntityTypeRepository {
  private final DSLContext jooqContext;

  public List<RawEntityTypeSummary> getEntityTypeSummary(Set<UUID> entityTypeIds) {
    log.info("Fetching entityTypeSummary for ids: {}", entityTypeIds);
    Field<UUID> idField = field("id", UUID.class);
    Field<String> nameField = field("definition ->> 'name'", String.class);
    Field<Boolean> privateEntityField = field("(definition ->> 'private')::boolean", Boolean.class);

    Condition publicEntityCondition = or(field(privateEntityField).isFalse(), field(privateEntityField).isNull());
    Condition entityTypeIdCondition = isEmpty(entityTypeIds) ? trueCondition() : field("id").in(entityTypeIds);

    return jooqContext.select(idField, nameField)
      .from(table("entity_type_definition"))
      .where(entityTypeIdCondition.and(publicEntityCondition))
      .fetch()
      .map(
        row -> new RawEntityTypeSummary(row.get(idField), row.get(nameField))
      );
  }

  public record RawEntityTypeSummary(UUID id, String name) {}
}
