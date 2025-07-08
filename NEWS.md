# 4.0.x - Sunflower

# 4.0.11
-  [MODFQMMGR-838] Check for insert statements when checking for zombie queries

[MODFQMMGR-838]: https://folio-org.atlassian.net/browse/MODFQMMGR-838

# 4.0.10
- Remove trillium harvested views, move backported trillium views to sunflower script

# 4.0.9
- Make composite SRS ETs private

# 4.0.8
- [MODFQMMGR-786] Create SRS authority ET
- [MODFQMMGR-788] Add support for entity-type-level filters
- [MODFQMMGR-804] Create simple SRS record entity in FQM

[MODFQMMGR-786]: https://folio-org.atlassian.net/browse/MODFQMMGR-786
[MODFQMMGR-788]: https://folio-org.atlassian.net/browse/MODFQMMGR-788
[MODFQMMGR-804]: https://folio-org.atlassian.net/browse/MODFQMMGR-804

# 4.0.7
- Update translations

# 4.0.6
- [MODFQMMGR-518] Handle zombie queries
- [MODFQMMGR-750] Inline certain SQL conditions to improve performance
- [MODFQMMGR-760] Improve query purging logic to avoid deletion of active long-running queries
- [MODFQMMGR-776] Use case-insensitive comparison for boolean query fields
- [MODFQMMGR-779] Allow Instance's `Flag for deletion` field to be queried
- [MODFQMMGR-781] Update `QUERY_RETENTION_DURATION` to new `MOD_FQM_MANAGER_QUERY_RETENTION_DURATION`

[MODFQMMGR-518]: https://folio-org.atlassian.net/browse/MODFQMMGR-518
[MODFQMMGR-750]: https://folio-org.atlassian.net/browse/MODFQMMGR-750
[MODFQMMGR-760]: https://folio-org.atlassian.net/browse/MODFQMMGR-760
[MODFQMMGR-776]: https://folio-org.atlassian.net/browse/MODFQMMGR-776
[MODFQMMGR-779]: https://folio-org.atlassian.net/browse/MODFQMMGR-779
[MODFQMMGR-781]: https://folio-org.atlassian.net/browse/MODFQMMGR-781

# 4.0.5
- Revert MODORDSTOR-448

# 4.0.4
- [MODFQMMGR-749] Handle duplicate custom field aliases (#702)
- [MODORDSTOR-448] Change type of eresource.userLimit to string; udpated po-lines interface (#703)
- [MODFQMMGR-648] Add special source with not-so-special value source for org name/code (#704)
- Accept po-lines 12.0 and 13.0 (#706)
- [MODFQMMGR-758] Make 'Item - Former IDs' field non-queryable (#708)
- [MODFQMMGR-765] Add Flag for deletion field to Instance records
- Update translation strings

[MODFQMMGR-749]: https://folio-org.atlassian.net/browse/MODFQMMGR-749
[MODORDSTOR-448]: https://folio-org.atlassian.net/browse/MODORDSTOR-448
[MODFQMMGR-648]: https://folio-org.atlassian.net/browse/MODFQMMGR-648
[MODFQMMGR-758]: https://folio-org.atlassian.net/browse/MODFQMMGR-758
[MODFQMMGR-765]: https://folio-org.atlassian.net/browse/MODFQMMGR-765

# 4.0.3
- [MODFQMMGR-726]: Fix queries for 'Instance - Cataloged date' (#694)
- [MODFQMMGR-736]: Update Item ET fields
- [MODFQMMGR-747]: Fix export for PO entity type
- Make 'Instance - JSONB' field essential

[MODFQMMGR-726]: https://folio-org.atlassian.net/browse/MODFQMMGR-726
[MODFQMMGR-736]: https://folio-org.atlassian.net/browse/MODFQMMGR-736
[MODFQMMGR-747]: https://folio-org.atlassian.net/browse/MODFQMMGR-747

# 4.0.2
- [MODFQMMGR-723]: Introduce MAX_SIZE_EXCEEDED query status
- [MODFQMMGR-728]: Ensure tenant_id column is last component of result_id

[MODFQMMGR-723]: https://folio-org.atlassian.net/browse/MODFQMMGR-723
[MODFQMMGR-728]: https://folio-org.atlassian.net/browse/MODFQMMGR-728

# 4.0.1
- [MODFQMMGR-714]: Fix Fiscal year - Acquisition unit names field
- [MODFQMMGR-720]: Fix data retrieval for ledger_fund fields
- [MODFQMMGR-695]: Fix entity type cache bug in ECS environments
- [MODFQMMGR-709]: Fix PoLine value for Transaction - Source field
- [MODFQMMGR-681]: Don't include a record count in query results if the query failed

[MODFQMMGR-714]: https://folio-org.atlassian.net/browse/MODFQMMGR-714
[MODFQMMGR-720]: https://folio-org.atlassian.net/browse/MODFQMMGR-720
[MODFQMMGR-695]: https://folio-org.atlassian.net/browse/MODFQMMGR-695
[MODFQMMGR-709]: https://folio-org.atlassian.net/browse/MODFQMMGR-709
[MODFQMMGR-681]: https://folio-org.atlassian.net/browse/MODFQMMGR-681

# 4.0.0
- [MODFQMMGR-376]: Add textbox custom fields to the Users entity type (#588)
- [MODFQMMGR-613]: Implement optimized Contains and StartsWith operators
- [MODFQMMGR-635]: Add Query Parameter to retrieve all the ET
- [MODFQMMGR-642]: Use custom field UUIDs for BE custom field names (#595)
- [MODFQMMGR-645]: Make the entity types visible for stakeholder review and feedback
- [MODFQMMGR-655]: Fix bug in fiscal year, where it returns no results
- Update names and translations for simple entity types
- [MODFQMMGR-656]: Update tag list name in items
- Remove redundant speed comparison folder (#611)
- [MODFQMMGR-652]: Fix null/empty queries for jsonbArrayTypes (#610)
- [MODFQMMGR-659]: Simple entity adjustment - Fiscal year
- [MODFQMMGR-644]: Replace the DB sources in composite_po_instance with ET sources
- [MODFQMMGR-669]: Entity adjustments: expense class (#614)
- [MODFQMMGR-649]: Add permissions to composite_po_instance
- [MODFQMMGR-644]: Fix and update composite_po_instance
- [MODFQMMGR-661]: Fixing Ledger ET
- [MODFQMMGR-610]: Use new entity type join format (#612)
- [MODFQMMGR-668]: Create finace group ET
- [MODFQMMGR-669]: unhide expense class id (#627)
- [MODFQMMGR-675]: Add Custom Fields support for Order and Order Line entities (#623)
- [MODFQMMGR-610]: Clone entity types during flattening, to prevent mutation of cached ones (#630)
- [MODFQMMGR-672]: Entity adjustments: Invoices (#624)
- [MODFQMMGR-543]: Add support for ordering ET sources in composites and refactor column sorting within ET sources
- [MODFQMMGR-673]: Entity adjustments: Invoice line (#629)
- Fix contributor_name_type ET (#634)
- [MODFQMMGR-660]: Restructure the liquibase directory
- [MODFQMMGR-646]: Entity adjustments: voucher line
- [MODFQMMGR-677]: Entity adjustments: Vouchers
- [MODFQMMGR-533]: Fix simple_loan_type and add it to composites
- [MODFQMMGR-680]: Make Index title field in Instance ET essential
- [MODFQMMGR-671]: Make field essential for inventory
- [MODFQMMGR-511]: Retrieve dropdown values for Instance - Source field (#647)
- [MODFQMMGR-692]: Unhide simple_purchase_order
- [MODFQMMGR-158]: Entity type adjustments: simple_item
- [MODFQMMGR-683]: adding source view for budget
- [MODFQMMGR-684]: Make several entity types private
- [MODFQMMGR-700]: Handle target fields more intelligently for reused names (#659)
- [MODFQMMGR-693]: Add Composite Invoice Line ET (#657)
- [MODFQMMGR-683]: new Budget ET
- [FOLIO-4237]: Upgrade to Java 21
- [MODFQMMGR-676]: Add item effective location at check out to the loan entity
- [MODFQMMGR-685]: Add transaction entity type (#652)
- [MODFQMMGR-694]: Composite ET adjustments (#656)
- Change instance classification type to stringType (#665)
- [MODFQMMGR-605]: Adding pronouns field to Users
- [MODFQMMGR-703]: Entity adjustments
- [MODFQMMGR-702]: Reorder sources within composite entities
- [MODFQMMGR-705]: Remove @Transactional

[MODFQMMGR-376]: https://folio-org.atlassian.net/browse/MODFQMMGR-376
[MODFQMMGR-613]: https://folio-org.atlassian.net/browse/MODFQMMGR-613
[MODFQMMGR-635]: https://folio-org.atlassian.net/browse/MODFQMMGR-635
[MODFQMMGR-642]: https://folio-org.atlassian.net/browse/MODFQMMGR-642
[MODFQMMGR-645]: https://folio-org.atlassian.net/browse/MODFQMMGR-645
[MODFQMMGR-655]: https://folio-org.atlassian.net/browse/MODFQMMGR-655
[MODFQMMGR-656]: https://folio-org.atlassian.net/browse/MODFQMMGR-656
[MODFQMMGR-652]: https://folio-org.atlassian.net/browse/MODFQMMGR-652
[MODFQMMGR-659]: https://folio-org.atlassian.net/browse/MODFQMMGR-659
[MODFQMMGR-644]: https://folio-org.atlassian.net/browse/MODFQMMGR-644
[MODFQMMGR-669]: https://folio-org.atlassian.net/browse/MODFQMMGR-669
[MODFQMMGR-649]: https://folio-org.atlassian.net/browse/MODFQMMGR-649
[MODFQMMGR-644]: https://folio-org.atlassian.net/browse/MODFQMMGR-644
[MODFQMMGR-661]: https://folio-org.atlassian.net/browse/MODFQMMGR-661
[MODFQMMGR-610]: https://folio-org.atlassian.net/browse/MODFQMMGR-610
[MODFQMMGR-668]: https://folio-org.atlassian.net/browse/MODFQMMGR-668
[MODFQMMGR-669]: https://folio-org.atlassian.net/browse/MODFQMMGR-669
[MODFQMMGR-675]: https://folio-org.atlassian.net/browse/MODFQMMGR-675
[MODFQMMGR-610]: https://folio-org.atlassian.net/browse/MODFQMMGR-610
[MODFQMMGR-672]: https://folio-org.atlassian.net/browse/MODFQMMGR-672
[MODFQMMGR-543]: https://folio-org.atlassian.net/browse/MODFQMMGR-543
[MODFQMMGR-673]: https://folio-org.atlassian.net/browse/MODFQMMGR-673
[MODFQMMGR-660]: https://folio-org.atlassian.net/browse/MODFQMMGR-660
[MODFQMMGR-646]: https://folio-org.atlassian.net/browse/MODFQMMGR-646
[MODFQMMGR-677]: https://folio-org.atlassian.net/browse/MODFQMMGR-677
[MODFQMMGR-533]: https://folio-org.atlassian.net/browse/MODFQMMGR-533
[MODFQMMGR-680]: https://folio-org.atlassian.net/browse/MODFQMMGR-680
[MODFQMMGR-671]: https://folio-org.atlassian.net/browse/MODFQMMGR-671
[MODFQMMGR-511]: https://folio-org.atlassian.net/browse/MODFQMMGR-511
[MODFQMMGR-692]: https://folio-org.atlassian.net/browse/MODFQMMGR-692
[MODFQMMGR-158]: https://folio-org.atlassian.net/browse/MODFQMMGR-158
[MODFQMMGR-683]: https://folio-org.atlassian.net/browse/MODFQMMGR-683
[MODFQMMGR-684]: https://folio-org.atlassian.net/browse/MODFQMMGR-684
[MODFQMMGR-700]: https://folio-org.atlassian.net/browse/MODFQMMGR-700
[MODFQMMGR-693]: https://folio-org.atlassian.net/browse/MODFQMMGR-693
[MODFQMMGR-683]: https://folio-org.atlassian.net/browse/MODFQMMGR-683
[MODFQMMGR-676]: https://folio-org.atlassian.net/browse/MODFQMMGR-676
[MODFQMMGR-685]: https://folio-org.atlassian.net/browse/MODFQMMGR-685
[MODFQMMGR-694]: https://folio-org.atlassian.net/browse/MODFQMMGR-694
[MODFQMMGR-605]: https://folio-org.atlassian.net/browse/MODFQMMGR-605
[MODFQMMGR-703]: https://folio-org.atlassian.net/browse/MODFQMMGR-703
[MODFQMMGR-702]: https://folio-org.atlassian.net/browse/MODFQMMGR-702
[MODFQMMGR-705]: https://folio-org.atlassian.net/browse/MODFQMMGR-705
[FOLIO-4237]: https://folio-org.atlassian.net/browse/FOLIO-4237

# 3.0.x - Ramsons

## 3.0.14
- [UXPROD-5315] Backport SRS entity types to Ramsons

[UXPROD-5315]: https://folio-org.atlassian.net/browse/UXPROD-5315

## 3.0.13
- [MODFQMMGR-748] Add special source for organization

[MODFQMMGR-748]: https://folio-org.atlassian.net/browse/MODFQMMGR-748

## 3.0.12
- [MODFQMMGR-658], [MODFQMMGR-650], [MODFQMMGR-643], [MODFQMMGR-649]: Add missing permissions to private entity types
- [MODFQMMGR-649] additioanally tweaks some settings on composite_voucher_line_totals_per_account to make it more usable

[MODFQMMGR-658]: https://folio-org.atlassian.net/browse/MODFQMMGR-658
[MODFQMMGR-650]: https://folio-org.atlassian.net/browse/MODFQMMGR-650
[MODFQMMGR-643]: https://folio-org.atlassian.net/browse/MODFQMMGR-643
[MODFQMMGR-649]: https://folio-org.atlassian.net/browse/MODFQMMGR-649

## 3.0.11
- [MODFQMMGR-657](https://folio-org.atlassian.net/browse/MODFQMMGR-657): Fix export for Organizations ET

## 3.0.10
- [MODFQMMGR-639](https://folio-org.atlassian.net/browse/MODFQMMGR-639):  Add acquisition-units interface dependency to module descriptor

## 3.0.9
- Add instance-formats interface to module descriptor

## 3.0.8
- Fix user-friendly queries and dropdown values for various fields
- [MODFQMMGR-618]: Organizations migration id/name/code fixes
- [MODFQMMGR-619]: Handle hidden POL fields
- [MODFQMMGR-622]: Turn JSONB arrays into Java arrays when retrieving results
- [MODFQMMGR-623]: Resolve query failures against loan policy field
- [MODFQMMGR-625]: Aggregate dropdown values from all tenants in ECS environments
- [MODFQMMGR-630]: Add generic exception handler

[MODFQMMGR-618]: https://folio-org.atlassian.net/browse/MODFQMMGR-618
[MODFQMMGR-619]: https://folio-org.atlassian.net/browse/MODFQMMGR-619
[MODFQMMGR-622]: https://folio-org.atlassian.net/browse/MODFQMMGR-622
[MODFQMMGR-623]: https://folio-org.atlassian.net/browse/MODFQMMGR-623
[MODFQMMGR-625]: https://folio-org.atlassian.net/browse/MODFQMMGR-625
[MODFQMMGR-630]: https://folio-org.atlassian.net/browse/MODFQMMGR-630

## 3.0.7
- [MODFQMMGR-548]: Add new `jsonbArrayType` data type
- [MODFQMMGR-522]: Increase thread pool size to 12
- [MODFQMMGR-606]: Support migration of old organization code field operators

[MODFQMMGR-548]: https://folio-org.atlassian.net/browse/MODFQMMGR-548
[MODFQMMGR-522]: https://folio-org.atlassian.net/browse/MODFQMMGR-522
[MODFQMMGR-606]: https://folio-org.atlassian.net/browse/MODFQMMGR-606

## 3.0.6
- [MODFQMMGR-599]: Add migration to remove query conditions that use $ne on UUID-type fields
- [MODFQMMGR-601]: Fix bug with the instance date_2 field implementation
- [MODFQMMGR-602]: Add migration for ID -> name field changes

[MODFQMMGR-599]: https://folio-org.atlassian.net/browse/MODFQMMGR-599
[MODFQMMGR-601]: https://folio-org.atlassian.net/browse/MODFQMMGR-601
[MODFQMMGR-602]: https://folio-org.atlassian.net/browse/MODFQMMGR-602

## 3.0.5
- [MODFQMMGR-577]: Fix env var typo in module descriptor and update documentation
- [MODFQMMGR-588]: Additional migration scripts
- [MODFQMMGR-510]: Cancel running queries in DB upon request
- more fix
- [MODFQMMGR-591]: Fix bug where FQM was sending multiple (and different) tenant headers in some API requests

[MODFQMMGR-577]: https://folio-org.atlassian.net/browse/MODFQMMGR-577
[MODFQMMGR-588]: https://folio-org.atlassian.net/browse/MODFQMMGR-588
[MODFQMMGR-510]: https://folio-org.atlassian.net/browse/MODFQMMGR-510
[MODFQMMGR-591]: https://folio-org.atlassian.net/browse/MODFQMMGR-591

## 3.0.4
- [MODFQMMGR-577]: Reduce the default cache expiration time for the entity type cache
- [MODFQMMGR-583]: Use available indexes for more fields

[MODFQMMGR-577]: https://folio-org.atlassian.net/browse/MODFQMMGR-577
[MODFQMMGR-583]: https://folio-org.atlassian.net/browse/MODFQMMGR-583

## 3.0.3
* Add DB_HOST_READER env variable to module descriptor, set to `""`
* [MODFQMMGR-575]: Fix bug where query results would be double-counted
* [MODFQMMGR-576]: Make field Exchange rate — Rate queryable
* [MODFQMMGR-582]: Update fields that are visible by default when viewing list results

[MODFQMMGR-575]: https://folio-org.atlassian.net/browse/MODFQMMGR-575
[MODFQMMGR-576]: https://folio-org.atlassian.net/browse/MODFQMMGR-576
[MODFQMMGR-582]: https://folio-org.atlassian.net/browse/MODFQMMGR-582

## 3.0.2
- [MODFQMMGR-532]: Aggregate languages from all tenants in ECS environments
- [MODFQMMGR-566]: Make the available value API case-insensitive
- [MODFQMMGR-570]: Add missing "permissions" interfaces to the module descriptor
- [MODFQMMGR-573] Consider converted date for timezone offsets, rather than system time

[MODFQMMGR-532]: https://folio-org.atlassian.net/browse/MODFQMMGR-532
[MODFQMMGR-566]: https://folio-org.atlassian.net/browse/MODFQMMGR-566
[MODFQMMGR-570]: https://folio-org.atlassian.net/browse/MODFQMMGR-570
[MODFQMMGR-573]: https://folio-org.atlassian.net/browse/MODFQMMGR-573

## 3.0.1
- [MODFQMMGR-558] Add acquisition unit names to the Organizations entity type
- [MODFQMMGR-563] Add /query/contents/privileged endpoint to request query results on behalf of another user
  - This includes a minor version bump for the `fqm-query` interface
- [MODFQMMGR-544] Remove the last materialized view 🎉
- [MODFQMMGR-555] Add categories to the Organizations entity type
- [MODFQMMGR-568] Remove callbacks to simplify code

[MODFQMMGR-558]: https://folio-org.atlassian.net/browse/MODFQMMGR-558
[MODFQMMGR-563]: https://folio-org.atlassian.net/browse/MODFQMMGR-563
[MODFQMMGR-544]: https://folio-org.atlassian.net/browse/MODFQMMGR-544
[MODFQMMGR-555]: https://folio-org.atlassian.net/browse/MODFQMMGR-555
[MODFQMMGR-568]: https://folio-org.atlassian.net/browse/MODFQMMGR-568

## 3.0.0
- Many (many) bug fixes and entity type data additions
- [MODFQMMGR-523](https://folio-org.atlassian.net/browse/MODFQMMGR-523) Upgrade `holdings-storage` to 8.0
- [MODFQMMGR-227](https://folio-org.atlassian.net/browse/MODFQMMGR-227) Remove derived_table_name column from entity type table
- [MODFQMMGR-203](https://folio-org.atlassian.net/browse/MODFQMMGR-203) New, simpler, entity type management system
- [MODFQMMGR-88](https://folio-org.atlassian.net/browse/MODFQMMGR-88) Add back-end translation guidelines
- [MODFQMMGR-174](https://folio-org.atlassian.net/browse/MODFQMMGR-174) Add entity type creator dev tool to simplify creating/maintaining entity types
- [UXPROD-4500](https://folio-org.atlassian.net/browse/UXPROD-4500) Add support for entity-type-level permissions
- [MODFQMMGR-222](https://folio-org.atlassian.net/browse/MODFQMMGR-222) Add resource allocation guidance to README
- [MODFQMGMR-303](https://folio-org.atlassian.net/browse/MODFQMGMR-303) Add support for the new stringUUIDType data type
- [MODFQMMGR-308](https://folio-org.atlassian.net/browse/MODFQMMGR-308) Change data types to make better use of indexes
- [MODFQMMGR-333](https://folio-org.atlassian.net/browse/MODFQMMGR-333) Update API to support fetching entity types with missing permissions
- [MODFQMMGR-230](https://folio-org.atlassian.net/browse/MODFQMMGR-230) Add support for composite entity types
- [UXPROD-4868](https://folio-org.atlassian.net/browse/UXPROD-4868) Recreate all entity types using the composite model
- [MODFQMMGR-390](https://folio-org.atlassian.net/browse/MODFQMMGR-390) Add ability to hide fields
- [MODFQMMGR-394](https://folio-org.atlassian.net/browse/MODFQMMGR-394) Expose JSONB as a hidden field in most entity types
- [UXPROD-4881](https://folio-org.atlassian.net/browse/UXPROD-4881) Add support for automatic query migration, to prevent breaking changes
- [MODFQMMGR-382](https://folio-org.atlassian.net/browse/MODFQMMGR-382) Add support for limiting non-essential columns when inheriting entity types
- [MODFQMMGR-225](https://folio-org.atlassian.net/browse/MODFQMMGR-225) Enable cross-tenant querying in ECS environments
- [MODFQMMGR-415](https://folio-org.atlassian.net/browse/MODFQMMGR-415) Cache entity type definitions
- [MODFQMMGR-453](https://folio-org.atlassian.net/browse/MODFQMMGR-453) Add local development permissions bypass (THIS SHOULD NOT BE USED IN PRODUCTION!)
- [MODFQMMGR-494](https://folio-org.atlassian.net/browse/MODFQMMGR-494) Update permission names
- [MODFQMMGR-496](https://folio-org.atlassian.net/browse/MODFQMMGR-496) Create unique permissions for each API endpoint
- [MODFQMMGR-525](https://folio-org.atlassian.net/browse/MODFQMMGR-525) Use folio-spring-support 8.3.0
- [UIPQB-144](https://folio-org.atlassian.net/browse/UIPQB-144) Remove the "not equal" operator from the UUID types

# 2.0.x - Quesnelia

## 2.0.5
- [MODFQMMGR-331](https://folio-org.atlassian.net/browse/MODFQMMGR-331) Retry materialized view refreshes on failure

## 2.0.4
- [MODFQMMGR-289](https://folio-org.atlassian.net/browse/MODFQMMGR-289) Fix bug where custom fields were not queryable
- [MODFQMMGR-290](https://folio-org.atlassian.net/browse/MODFQMMGR-290) Fix bug in the Instances entity type where data was incorrectly filtered out in some cases


## 2.0.3
- [MODFQMMGR-202](https://folio-org.atlassian.net/browse/MODFQMMGR-202) Include system exchange rate when set exchange rate isn't active

## 2.0.2
- [MODFQMMGR-217](https://folio-org.atlassian.net/browse/MODFQMMGR-217) Fix read/write split bug
- [MODFQMMGR-217](https://folio-org.atlassian.net/browse/MODFQMMGR-217) Lower contributor-name-types dependency version
- [MODFQMMGR-213](https://folio-org.atlassian.net/browse/MODFQMMGR-213) Limit available currencies in queries to only those actually supported in FOLIO


## 2.0.1
- Update translations
- [MODFQMMGR-216](https://folio-org.atlassian.net/browse/MODFQMMGR-216) Fix NPE


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

# 1.0.x - Poppy

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
