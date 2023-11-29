package org.folio.fqm.repository;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.val;

@Repository
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class QueryDetailsRepository {
  private static final Field<UUID> ENTITY_TYPE_ID = field("entity_type_id", UUID.class);
  private static final Field<UUID> QUERY_ID = field("query_id", UUID.class);

  private final DSLContext jooqContext;

  public UUID getEntityTypeId(UUID queryId) {
    return jooqContext.select(ENTITY_TYPE_ID)
      .from(table("query_details"))
      .where(QUERY_ID.eq(val(queryId)))
      .fetchOne(ENTITY_TYPE_ID);
  }
}
