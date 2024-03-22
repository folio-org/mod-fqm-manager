# 2.0.x

## 2.0.0
- [MODFQMMGR-87](https://folio-org.atlassian.net/browse/MODFQMMGR-87) Move FQM-specific code from lib-fqm-query-processor
- [MODFQMMGR-78](https://folio-org.atlassian.net/browse/MODFQMMGR-78) Add back-end localization to entity types
- [MODFQMMGR-91](https://folio-org.atlassian.net/browse/MODFQMMGR-91), [MODFQMMGR-93](https://folio-org.atlassian.net/browse/MODFQMMGR-93), [MODFQMMGR-112](https://folio-org.atlassian.net/browse/MODFQMMGR-112), [MODFQMMGR-120](https://folio-org.atlassian.net/browse/MODFQMMGR-120), [MODFQMMGR-121](https://folio-org.atlassian.net/browse/MODFQMMGR-121), [MODFQMMGR-152](https://folio-org.atlassian.net/browse/MODFQMMGR-152) Add Purchase order lines entity type
- [MODFQMMGR-79](https://folio-org.atlassian.net/browse/MODFQMMGR-79) Add support for filter value getters
- [MODFQMMGR-80](https://folio-org.atlassian.net/browse/MODFQMMGR-80) Build the SELECT clause from field accessors defined in the entity type
- [MODFQMMGR-97](https://folio-org.atlassian.net/browse/MODFQMMGR-97) Fix bug where query failures with long failure reasons result in a SQL error
- [MODFQMMGR-81](https://folio-org.atlassian.net/browse/MODFQMMGR-81) Refactor case sensitivity in queries, for performance
- [MODFQMMGR-98](https://folio-org.atlassian.net/browse/MODFQMMGR-98) Add filterValueGetters for the Items entity type
- [MODFQMMGR-99](https://folio-org.atlassian.net/browse/MODFQMMGR-99), [MODFQMMGR-155](https://folio-org.atlassian.net/browse/MODFQMMGR-155) Add support for single-checkbox custom fields
- [MODFQMMGR-102](https://folio-org.atlassian.net/browse/MODFQMMGR-102) Rearrange the columns in the query_results PK
- [MODFQMMGR-106](https://folio-org.atlassian.net/browse/MODFQMMGR-106) Remove transactions and fix query result count
- [MODFQMMGR-111](https://folio-org.atlassian.net/browse/MODFQMMGR-111) Add array-based fields and optional GROUP BY clause
- [MODFQMMGR-113](https://folio-org.atlassian.net/browse/MODFQMMGR-113) Rework array operators to avoid substring comparisons
- [MODFQMMGR-110](https://folio-org.atlassian.net/browse/MODFQMMGR-110) Add filterValueGetters for the Loans entity type
- [MODFQMMGR-109](https://folio-org.atlassian.net/browse/MODFQMMGR-109) Add filterValueGetters for the Users entity type
- [MODFQMMGR-125](https://folio-org.atlassian.net/browse/MODFQMMGR-125) Add _deleted field for content items that have been deleted
- [MODFQMMGR-116](https://folio-org.atlassian.net/browse/MODFQMMGR-116) Add support for value functions on fields
- [MODFQMMGR-116](https://folio-org.atlassian.net/browse/MODFQMMGR-116) Add valueFunctions for the Items, Loans, Users, and POL entity types
- [MODFQMMGR-119](https://folio-org.atlassian.net/browse/MODFQMMGR-119) Add empty FQL operator
- [MODFQMMGR-101](https://folio-org.atlassian.net/browse/MODFQMMGR-101) Make all item status available for queries, instead of only showing the ones currently in use
- [MODFQMMGR-150](https://folio-org.atlassian.net/browse/MODFQMMGR-150), [MODFQMMGR-159](https://folio-org.atlassian.net/browse/MODFQMMGR-159) Support localization of custom fields and deeply nested object properties
- [MODFQMMGR-134](https://folio-org.atlassian.net/browse/MODFQMMGR-134) Implement support for value source APIs
- [MODFQMMGR-137](https://folio-org.atlassian.net/browse/MODFQMMGR-137) Use list of strings as unique record identifier for lists
- [MODFQMMGR-151](https://folio-org.atlassian.net/browse/MODFQMMGR-151) Support nested object queries
- [MODFQMMGR-129](https://folio-org.atlassian.net/browse/MODFQMMGR-129) Add Instances entity type
- [MODFQMMGR-126](https://folio-org.atlassian.net/browse/MODFQMMGR-126) Add Holdings entity type
- [MODFQMMGR-173](https://folio-org.atlassian.net/browse/MODFQMMGR-173) Fix DB migration bug
- [MODFQMMGR-131](https://folio-org.atlassian.net/browse/MODFQMMGR-131) Add contains_all and not_contains_all operators
- [MODFQMMGR-183](https://folio-org.atlassian.net/browse/MODFQMMGR-183) Add all id columns to query fields
- [MODFQMMGR-146](https://folio-org.atlassian.net/browse/MODFQMMGR-146) Add Organizations - vendor info entity type
- [MODFQMMGR-176](https://folio-org.atlassian.net/browse/MODFQMMGR-176) Add Organization - contact info entity type
- [MODFQMMGR-154](https://folio-org.atlassian.net/browse/MODFQMMGR-154) Add statistical codes to the Items entity type
- [MODFQMMGR-141](https://folio-org.atlassian.net/browse/MODFQMMGR-141) Add queryable flag
- [MODFQMMGR-130](https://folio-org.atlassian.net/browse/MODFQMMGR-130) Add contains_any and not_contains_any FQL operators
- [MODFQMMGR-186](https://folio-org.atlassian.net/browse/MODFQMMGR-186) Bump Spring Boot dependency version
- [MODFQMMGR-191](https://folio-org.atlassian.net/browse/MODFQMMGR-191) Make array fields non-queryable
- Increase memory in module descriptor to 600 MiB
- [MODFQMMGR-201](https://folio-org.atlassian.net/browse/MODFQMMGR-201) Add limit param to value source API calls
- [MODFQMMGR-210](https://folio-org.atlassian.net/browse/MODFQMMGR-210), [MODFQMMGR-210](https://folio-org.atlassian.net/browse/MODFQMMGR-210) Add second, asynchronous, liquibase run to createOrUpdateTenant() to populate materialized views in the "slow" context
- [MODFQMMGR-181](https://folio-org.atlassian.net/browse/MODFQMMGR-181) Sort fields alphabetically

# 1.0.x

## 1.0.3
- [MODFQMMGR-58](https://issues.folio.org/browse/MODFQMMGR-58) Refactor drv_item_callnumber_location view
- [MODFQMMGR-76](https://issues.folio.org/browse/MODFQMMGR-76) Periodically refresh materialized views

## 1.0.2
- Remove the instance_title_searchable field from the Items entity type
- Purge old query results based on query start date/time instead of the end date/time
- Fix bug in user preferred contact type
- Update the provided `_tenant` interface in the module descriptor to 2.0

## 1.0.1
- [MODFQMMGR-57](https://issues.folio.org/browse/MODFQMMGR-57) Use a different version of f_unaccent(), to allow us to make use of an index
- [MODFQMMGR-49](https://issues.folio.org/browse/MODFQMMGR-49) Fix effective call number
- [MODFQMMGR-51](https://issues.folio.org/browse/MODFQMMGR-51) Refactor item views to use instance metadata indexes
- [MODFQMMGR-67](https://issues.folio.org/browse/MODFQMMGR-67) Enable batched inserts
- [MODFQMMGR-71](https://issues.folio.org/browse/MODFQMMGR-71) Update item and user entity types
- [MODFQMMGR-31](https://issues.folio.org/browse/MODFQMMGR-31) Fix Users dropdown

## 1.0.0
- Initial release
