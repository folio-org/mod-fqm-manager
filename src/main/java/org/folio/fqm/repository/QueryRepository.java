package org.folio.fqm.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.fqm.domain.Query;
import org.folio.fqm.domain.QueryStatus;
import org.folio.fqm.exception.EntityTypeNotFoundException;
import org.folio.fqm.service.EntityTypeService;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.QueryIdentifier;
import org.jooq.DSLContext;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

import static org.jooq.impl.DSL.*;

@Repository
@RequiredArgsConstructor
@Log4j2
public class QueryRepository {

  private static final String QUERY_DETAILS_TABLE = "query_details";
  private static final String QUERY_ID = "query_id";

  private final DSLContext jooqContext;

  // This EntityTypeService is needed for a temporary workaround to maintain compatibility
  // with the UI. Once the UI has been updated to include the fields parameter in a query request,
  // this repository can be removed
  private final EntityTypeService entityTypeService;

  public QueryIdentifier saveQuery(Query query) {
    jooqContext.insertInto(table(QUERY_DETAILS_TABLE))
      .set(field(QUERY_ID), query.queryId())
      .set(field("entity_type_id"), query.entityTypeId())
      .set(field("fql_query"), query.fqlQuery())
      // Once the UI has been updated to send fields in the query request, the ternary operator can be removed
      // from the below line
      .set(field("fields"), CollectionUtils.isEmpty(query.fields()) ?
        getFieldsFromEntityType(query.entityTypeId()).toArray(new String[0]) : query.fields().toArray(new String[0]))
      .set(field("created_by"), query.createdBy())
      .set(field("start_date"), query.startDate())
      .set(field("status"), query.status().toString())
      .execute();
    return new QueryIdentifier().queryId(query.queryId());
  }

  public void updateQuery(UUID queryId, QueryStatus queryStatus, OffsetDateTime endDate, String failureReason) {
    jooqContext.update(table(QUERY_DETAILS_TABLE))
      .set(field("status"), queryStatus.toString())
      .set(field("end_date"), endDate)
      .set(field("failure_reason"), failureReason)
      .where(field(QUERY_ID).eq(queryId))
      .execute();
  }

  @Cacheable(value="queryCache", condition="#useCache==true")
  public Optional<Query> getQuery(UUID queryId, boolean useCache) {
    return Optional.ofNullable(jooqContext.select()
      .from(table(QUERY_DETAILS_TABLE))
      .where(field(QUERY_ID).eq(queryId))
      .fetchOneInto(Query.class));
  }

  public List<UUID> getQueryIdsStartedBefore(Duration duration) {
    return jooqContext.select(field(QUERY_ID))
      .from(table(QUERY_DETAILS_TABLE))
      .where(field("start_date").
        lessOrEqual(OffsetDateTime.now().minus(duration)))
      .fetchInto(UUID.class);
  }

  public void deleteQueries(List<UUID> queryId) {
    jooqContext.deleteFrom(table(QUERY_DETAILS_TABLE))
      .where(field(QUERY_ID).in(queryId))
      .execute();
  }

  // The below method retrieves all fields from the entity type. We are using it as a
  // temporary workaround to supply fields in the query request. This maintains compatibility
  // until the UI has been updated to pass the fields parameter in a query request.
  private List<String> getFieldsFromEntityType(UUID entityTypeId) {
    EntityType entityType = entityTypeService.getEntityTypeDefinition(entityTypeId)
      .orElseThrow(() -> new EntityTypeNotFoundException(entityTypeId));
    List<String> fields = new ArrayList<>();
    entityType
      .getColumns()
      .forEach(col -> fields.add(col.getName()));
    return fields;
  }
}
