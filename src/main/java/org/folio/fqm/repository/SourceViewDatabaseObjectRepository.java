package org.folio.fqm.repository;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.fqm.domain.SourceViewDefinition;
import org.folio.fqm.domain.SourceViewDefinition.SourceViewDependency;
import org.folio.fqm.domain.SourceViewRecord;
import org.folio.spring.FolioExecutionContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Repository;

@Log4j2
@Repository
@AllArgsConstructor
public class SourceViewDatabaseObjectRepository {

  private final SourceViewRecordRepository sourceViewRecordRepository;

  private final FolioExecutionContext folioExecutionContext;

  private final DSLContext jooqContext;

  /**
   * Install a single source view. This will create a record in `source_views` table, too.
   */
  public void installSingleSourceView(SourceViewDefinition view) {
    jooqContext.transaction(transaction -> transaction.dsl().createOrReplaceView(view.name()).as(view.sql()).execute());

    SourceViewRecord newRecord = SourceViewRecord
      .builder()
      .name(view.name())
      .definition(view.sql())
      .sourceFile(view.sourceFilePath())
      .lastUpdated(Instant.now())
      .build();
    sourceViewRecordRepository.save(newRecord);
  }

  /**
   * Install, update, and remove source views from the database. Accompanying {@link SourceViewRecord}s
   * will also be created and replace the existing records in the `source_views` table.
   *
   * @param definitions the source view definitions, keyed by view name
   * @param toInstall   list of views to install
   * @param toUpdate    list of views to recreate with updated definitions
   * @param toRemove    list of views to remove
   */
  public void persistSourceViews(
    Map<String, SourceViewDefinition> definitions,
    Collection<String> toInstall,
    Collection<String> toUpdate,
    Collection<String> toRemove
  ) {
    jooqContext.transaction(transaction -> {
      // TODO: [MODFQMMGR-1014] Replace formatted SQL string with jOOQ's fluent API once 3.21 is released
      toRemove.forEach(k -> transaction.dsl().execute("DROP VIEW IF EXISTS \"" + k + "\" CASCADE"));
      toInstall.forEach(k -> transaction.dsl().createOrReplaceView(k).as(definitions.get(k).sql()).execute());
      toUpdate.forEach(k -> {
        // TODO: [MODFQMMGR-1014] Replace formatted SQL string with jOOQ's fluent API once 3.21 is released
        transaction.dsl().execute("DROP VIEW IF EXISTS \"" + k + "\" CASCADE");
        transaction.dsl().createOrReplaceView(k).as(definitions.get(k).sql()).execute();
      });
    });

    log.info("All done persisting views to DB, updating source_views table...");

    sourceViewRecordRepository.deleteAllById(toRemove);
    sourceViewRecordRepository.deleteAllById(toUpdate);
    sourceViewRecordRepository.saveAll(
      Stream
        .concat(toInstall.stream(), toUpdate.stream())
        .map(name -> {
          SourceViewDefinition def = definitions.get(name);
          return SourceViewRecord
            .builder()
            .name(def.name())
            .definition(def.sql())
            .sourceFile(def.sourceFilePath())
            .lastUpdated(Instant.now())
            .build();
        })
        .toList()
    );

    log.info("Source views table updated successfully");
  }

  public boolean doesSourceViewExistInDatabase(String viewName) {
    return (
      jooqContext
        .selectOne()
        .from(table(name("information_schema", "tables")))
        .where(
          List.of(
            field("table_schema").eq("%s_mod_fqm_manager".formatted(folioExecutionContext.getTenantId())),
            field("table_name").eq(viewName)
          )
        )
        .fetchOne() !=
        null
    );
  }

  /**
   * Get the list of views present in our module; used to reconcile and ensure source_views is accurate
   */
  protected Set<String> getInstalledSourceViewsFromDatabase() {
    return jooqContext
      .select(field("table_name", String.class))
      .from(table(name("information_schema", "tables")))
      .where(
        List.of(
          field("table_schema").eq("%s_mod_fqm_manager".formatted(folioExecutionContext.getTenantId())),
          field("table_type").eq("VIEW")
        )
      )
      .fetchSet(field("table_name", String.class));
  }

  public Set<SourceViewDependency> getAvailableSourceViewDependencies(String safeCentralTenantId) {
    // in testing, this yields <2500 tables for a full FOLIO install. This is not the most efficient way to do this,
    // however, it's a lot better than sending hundreds of individual queries to check for existence one at a time,
    // and a lot more readable than crafting a massive dynamic query with hundreds of OR clauses.
    Result<Record> result = jooqContext
      .select(List.of(field("table_schema", String.class), field("table_name", String.class)))
      .from(table(name("information_schema", "tables")))
      // Select all schemas for the current tenant, plus the central tenant's mod_search schema (since member tenants
      // may not have their own mod-search schema in ECS environments).
      .where(
        field("table_schema").startsWith(folioExecutionContext.getTenantId() + "_mod_")
          .or(field("table_schema").eq(safeCentralTenantId + "_mod_search"))
      )
      .fetch();

    log.info("Discovered {} available dependency tables in the database", result.size());

    return result
      .stream()
      .map(r ->
        new SourceViewDependency(r.get(field("table_schema", String.class)), r.get(field("table_name", String.class)))
      )
      .collect(Collectors.toSet());
  }

  /**
   * Get all present materialized views. We want to remove these legacy ones if encountered.
   */
  protected List<String> getMaterializedViewsFromDatabase() {
    return jooqContext
      .select(field("matviewname", String.class))
      .from(table(name("pg_matviews")))
      .where(field("schemaname").eq("%s_mod_fqm_manager".formatted(folioExecutionContext.getTenantId())))
      .fetch(field("matviewname", String.class));
  }

  /**
   * Removes all legacy materialized from the database
   */
  public void purgeMaterializedViewsIfPresent() {
    List<String> materializedViews = getMaterializedViewsFromDatabase();
    if (!materializedViews.isEmpty()) {
      log.warn("The following legacy materialized views exist in the database: {}", materializedViews);
      log.warn("Removing {} materialized views: {}", materializedViews.size(), materializedViews);
      jooqContext.transaction(transaction ->
        materializedViews.forEach(viewName ->
          // TODO: [MODFQMMGR-1014] Replace formatted SQL string with jOOQ's fluent API once 3.21 is released
          transaction.dsl().execute("DROP MATERIALIZED VIEW IF EXISTS \"" + viewName + "\" CASCADE")
        )
      );
    }
  }

  /**
   * Ensures that all {@link SourceViewRecord}s have matching database objects and vice versa
   */
  public void verifySourceViewRecordsMatchesDatabase() throws IOException {
    Set<String> realViews = this.getInstalledSourceViewsFromDatabase();
    List<String> expectedViews = sourceViewRecordRepository.findAll().stream().map(SourceViewRecord::getName).toList();

    Collection<String> missingViews = CollectionUtils.subtract(expectedViews, realViews);
    if (!missingViews.isEmpty()) {
      log.error(
        "The following source views were previously created but are now missing from the database: {}. Removing these from source_views table...",
        missingViews
      );
      sourceViewRecordRepository.deleteAllById(missingViews);
    }

    Collection<String> unexpectedViews = CollectionUtils.subtract(realViews, expectedViews);
    if (!unexpectedViews.isEmpty()) {
      log.warn(
        "The following unexpected source views exist in the database: {}. These are likely from a previous installation where views were created by Liquibase and will be removed.",
        unexpectedViews
      );
      jooqContext.transaction(transaction ->
        unexpectedViews.forEach(viewName ->
          // TODO: [MODFQMMGR-1014] Replace formatted SQL string with jOOQ's fluent API once 3.21 is released
          transaction.dsl().execute("DROP VIEW IF EXISTS \"" + viewName + "\" CASCADE")
        )
      );
    }
  }
}
