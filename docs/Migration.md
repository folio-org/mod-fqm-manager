# Query Migration

Entity types and their fields change over time, be it adding fields, moving them between entity types, or completely rethinking the way some fields are handled. As such, we have a robust migration system to ensure that consuming apps will not break, and their queries will continue to work despite any internal FQM changes.

- [Query versions](#query-versions)
- [Updating a query](#updating-a-query)
- [Writing migrations](#writing-migrations)
  - [Changes](#changes)
    - [Entity type changes](#entity-type-changes)
    - [Field changes](#field-changes)
  - [Warnings](#warnings)
    - [Entity type warnings](#entity-type-warnings)
      - [DeprecatedEntityWarning](#deprecatedentitywarning)
      - [RemovedEntityWarning](#removedentitywarning)
    - [Field warnings](#field-warnings)
      - [Example](#example)
      - [DeprecatedFieldWarning](#deprecatedfieldwarning)
      - [QueryBreakingWarning](#querybreakingwarning)
      - [RemovedFieldWarning](#removedfieldwarning)
      - [Additional warnings](#additional-warnings)
  - [Advanced migrations](#advanced-migrations)

## Query versions

The version of a query is stored inside the FQL string:

```json
{
  "_version": "3",
  "users.first_name": { "$eq": "John" }
}
```

These are arbitrary strings, and consuming applications should make no assumptions about them (they are currently integers, but may be changed in the future to commit hashes, module versions, or anything else).

Queries from Quesnelia or earlier will have no version associated with them and will be considered version `"0"`.

## Updating a query

To update a query, send it, the entity type ID, and a list of fields (if desired) to `/fqm/migrate`. See our [API documentation](https://dev.folio.org/reference/api/#mod-fqm-manager) for more information about this endpoint. Our module will return the updated query, entity type ID, and list of fields, all of which should be saved. Additionally, the response may contain [warnings](#warnings), meaning that some parts of the query or field list was unable to be migrated.

## Writing migrations

Any change to an entity type that results in a field being removed or renamed should result in a migration script. The easiest way to do this is to do the following:

1. Create a new migration strategy in `src/main/java/org/folio/fqm/migration/strategies` that extends `AbstractSimpleMigrationStrategy`
   - Your migration strategy should start with `V#`, where `#` is the version being migrated **from**.
1. Implement `getMaximumApplicableVersion` to return the last version to which the migration should be applied. Currently, this is the current integer version.
1. Implement `getLabel` with a developer-friendly description of what is happening; this will be logged and will be useful for debugging any errors. It is recommended to include ticket numbers or other references to make it easy to track down why this migration/change occurred in the first place, too.
1. Describe the [changes](#changes) in this migration by implementing any of the applicable methods.
1. Describe the [warnings](#warnings) in this migration by implementing any of the applicable methods.
1. Add your new strategy to `src/main/java/org/folio/fqm/migration/MigrationStrategyRepository.java`.
1. Update the `CURRENT_VERSION` in `src/main/java/org/folio/fqm/config/MigrationConfiguration.java`.
1. If something fancy is being done, or you want to go above and beyond, write a custom test. You can see `src/test/java/org/folio/fqm/migration/strategies/TestTemplate` and `V0POCMigrationTest` for a framework that can easily be extended for common test case formats.
   - Implementations of `AbstractSimpleMigrationStrategy` are automatically tested via `MigrationStrategyRepositoryTest`, however, this is just for basic smoke tests and contains no logic to test specifics of an actual migration.
   - See [Advanced migrations](#advanced-migrations) for more information

Not all use cases can be covered with `AbstractSimpleMigrationStrategy`; in that case, a custom migration will be needed. See [advanced migrations](#advanced-migrations) for more details.

### Changes

#### Entity type changes

This type of change covers when entity types change IDs. To describe these, implement `getEntityTypeChanges` and return a `Map<UUID, UUID>` between the original and migrated entity type IDs.

For example, this would denote the entity type with ID `00000000-0000-0000-0000-123456789012` being changed to `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa`:

```java
public Map<UUID, UUID> getEntityTypeChanges() {
  return Map.of("00000000-0000-0000-0000-123456789012", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
}
```

#### Field changes

This type of change covers when fields change names. To describe these, implement `getFieldChanges` and return a `Map<UUID, Map<String, String>>`, where the outer key is the **old** entity type ID (if the entity type is also subject to a [change](#entity-type-changes)) and the inner map is from the original field name to the new field names.

For example, this would denote a change in `00000000-0000-0000-0000-123456789012` where `fname` is now called `first_name` and `lname` is called `last_name`:

```java
public Map<UUID, UUID> getEntityTypeChanges() {
  return Map.of("00000000-0000-0000-0000-123456789012", Map.of(
    "fname", "first_name",
    "lname", "last_name"
  ));
}
```

There is a special case, `"*"`, which can be used as a key on the inner map to apply a transformation to all fields. For this, a value can include `%s` which will be formatted with the original field name. For example, this will prepend `user_` to all fields, resulting in `fname` becoming `user_fname`:

```java
public Map<UUID, UUID> getEntityTypeChanges() {
  return Map.of("00000000-0000-0000-0000-123456789012", Map.of(
    "*", "user_%s"
  ));
}
```

### Warnings

Sometimes, things do not go as planned, and we cannot be backwards compatible. A suite of `Warning` classes are provided to handle common use cases, as described below. Generally, you should use the factory methods when creating an `AbstractSimpleMigrationStrategy`, and the builders when creating a custom migration.

#### Entity type warnings

These warnings apply to an entity type as a whole, and currently encompass deprecation and removal of an entire entity type.

To declare these as part of a migration, implement `getEntityTypeWarnings` and return a `Map<UUID, Function<String, EntityTypeWarning>>`. The keys to this map are the entity type's ID and the values are factory functions which take a `String fql` in and return an `EntityTypeWarning`. To produce these factory methods, use the convenience methods on each warning class, as described below.

These require the original entity type's name as an input, as the handling logic is currently unable to fetch this on the fly.

##### DeprecatedEntityWarning

This warning indicates that an entity type is deprecated and will be removed in a future release. The warning may include an optional alternative which users may be able to use instead.

This warning will not present the FQL of the original query, as it is presumed the original entity type/query is still working at the time of the deprecation (use [RemovedEntityWarning](#removedentitywarning) if that is not the case).

**Usage:**

- `DeprecatedEntityWarning.withoutAlternative(String entityType)`
  - Example: `DeprecatedEntityWarning.withoutAlternative("current_et")` to denote that `current_et` is deprecated with no alternative.
- `DeprecatedEntityWarning.withAlternative(String entityType, String alternative)`
  - Example: `DeprecatedEntityWarning.withAlternative("current_et", "future_et")` to denote that `current_et` is deprecated and users may be able to use `future_et` instead.

##### RemovedEntityWarning

This warning indicates that an entity type is removed, and may optionally include an alternative which users may be able to use instead. When a migration results in this warning, the response will contain a special entity type ID `deadbeef-dead-dead-dead-deaddeadbeef`, pointing to an empty entity type with no fields. The query will additionally be empty (containing only the version), and the fields will be emptied out.

Due to the nature of this, the FQL of the original query will be returned in the warning, so that it is not lost.

**Usage:**

- `RemovedEntityWarning.withoutAlternative(String entityType)`
  - Example: `RemovedEntityWarning.withoutAlternative("current_et")` to denote that `current_et` is no longer available.
- `RemovedEntityWarning.withAlternative(String entityType, String alternative)`
  - Example: `RemovedEntityWarning.withAlternative("current_et", "future_et")` to denote that `current_et` is removed and users may be able to use `future_et` instead.

#### Field warnings

These warnings apply to a single field.

To declare these as part of a migration, implement `getFieldWarnings` and return a `Map<UUID, Map<String, BiFunction<String, String, FieldWarning>>>`. This map points from the entity type UUID to the field name to a factory method for the warning. To produce these factory methods, use the convenience methods on each warning class, as described below.

The factory method here accepts two strings, the field's name and the FQL used in it's query (if it was present in the query, or null if it is just a result field).

##### Example

In this example, `first_name` and `last_name` are no longer available, but the user may be able to get similar functionality from `last_name_first_name`. Additionally, `middle_name` is removed, and no longer available even via another field.

```java
public Map<UUID, Map<String, BiFunction<String, String, FieldWarning>>> getFieldWarnings() {
  return Map.of(
    "entity type ID",
    Map.ofEntries(
      Map.entry("first_name", RemovedFieldWarning.withAlternative("last_name_first_name")),
      Map.entry("last_name", RemovedFieldWarning.withAlternative("last_name_first_name")),
      Map.entry("middle_name", RemovedFieldWarning.withoutAlternative())
    )
  );
}
```

##### DeprecatedFieldWarning

This warning indicates that a field is deprecated and will be removed in a future release.

This warning will not present the FQL from the original query, as it is presumed the field is still working at the time of the deprecation (use [RemovedFieldWarning](#removedfieldwarning) if that is not the case).

**Usage:**

- `DeprecatedFieldWarning.build()`

##### QueryBreakingWarning

This warning indicates that a field is no longer able to be queried, and may optionally include an alternative which users may be able to use instead. The migration response will remove the field from the query only, but leave it in the fields list.

Due to the nature of this, the FQL from the original query (if applicable) will be returned in the warning, so that it is not lost.

**Usage:**

- `QueryBreakingWarning.withoutAlternative()`
  - Example: `QueryBreakingWarning.withoutAlternative()` to denote that this field is no longer queryable.
- `QueryBreakingWarning.withAlternative(String alternative)`
  - Example: `QueryBreakingWarning.withAlternative("future_field")` to denote that this field is no longer queryable and users may be able to use `future_field` instead.

##### RemovedFieldWarning

This warning indicates that a field has been removed, and may optionally include an alternative which users may be able to use instead. The migration response will remove the field from the query and fields list, potentially resulting in an empty query if this was the only field in use.

Due to the nature of this, the FQL from the original query (if applicable) will be returned in the warning, so that it is not lost.

**Usage:**

- `RemovedFieldWarning.withoutAlternative()`
  - Example: `RemovedFieldWarning.withoutAlternative()` to denote that this field is no longer available.
- `RemovedFieldWarning.withAlternative(String alternative)`
  - Example: `RemovedFieldWarning.withAlternative("future_field")` to denote that this field is removed and users may be able to use `future_field` instead.

##### Additional warnings

Warnings for more complex use cases include `OperatorBreakingWarning` and `ValueBreakingWarning`. These are only available for advanced migrations, and are not covered here.

### Advanced migrations

Advanced migrations that need to do more advanced logic or conditionals, or apply to a range of migration versions, require custom implementations of `MigrationStrategy`. Tutorials for this is outside the scope of this document, however, there are three methods to be implemented here which should unlock full power:

- `getLabel` should return a descriptive label for logging, just like `AbstractSimpleMigrationStrategy`
- `applies` can optionally be implemented to only run migration logic on matching queries
- `apply` is responsible for the full transformation of the query, adding warnings if applicable, and anything else necessary.
  - If the migration results in a breaking change, then make sure to set `hadBreakingChanges` to `true` on the
    `MigrationQueryInformtaion` object returned from `apply`. This prevents queries from running if they are simply not
    compatible with the running FQM release.

Helper functions are available in `MigrationUtils`; it is highly recommend to build off one of the existing migrations if possible.

**It is critical that, after `apply` is called, `applies` should return false. Otherwise, an infinite loop may occur.**
