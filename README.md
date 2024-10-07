# FQM
Copyright (C) 2023 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

FQM (FOLIO query machine) is the engine that takes in queries, processes queries, and provides answers.
FQM consolidates data from different modules within FOLIO in real time, allowing users to conduct
cross-application searches efficiently.

## Architecture
The mod-fqm-manager database schema consists of three types of database objects:

1. Source Views

Source views are used to project data from the operational tables. They provide a convenient way to access and query the data without directly impacting the performance of the operational tables. It is recommended to configure the source views to point to the read replicas of the operational tables. This ensures that the querying activities on the views do not interfere with the ongoing operations on the operational tables.
> **Note:** The source views  are prefixed with `src_`

2. Computed Views (AKA derived views)

Computed views are specifically designed for generating reports. These views are created by combining or joining multiple source views. The purpose of computed views is to reshape the data in a way that is suitable for reporting purposes. For example, if the source view contains data in the jsonb format, the computed views will present the corresponding data in a row and column format, making it easier to analyze and generate reports.

Computed views serve as a layer of abstraction over the underlying data, providing a simplified and structured representation for reporting purposes. By leveraging the power of computed views, users can efficiently retrieve and analyze data without the need for complex and resource-intensive queries.
To improve performance, some relatively static computed views may be [materialized](https://en.wikipedia.org/wiki/Materialized_view)
> **Note:** The computed/derived views are prefixed with `drv_`

3. Other Database Objects

Apart from the source views, computed views and materialized views the mod-fqm-manager database schema may contain other essential database objects such as tables, indexes or functions. These objects are used to support the overall functionality of the database and ensure efficient data management and retrieval.

By utilizing the combination of source views, computed views, and other relevant database objects, the mod-fqm-manager schema provides a robust foundation for data analysis and reporting within the system.

## Compiling
```bash
mvn clean install
```
## Environment Variables
| Name                                              | Default Value | Description                                               |
|---------------------------------------------------|---------------|-----------------------------------------------------------|
| DB_HOST                                           | localhost     | Postgres hostname                                         |
| DB_PORT                                           | 5432          | Postgres port                                             |
| DB_HOST_READER                                    | localhost     | Postgres hostname                                         |
| DB_PORT_READER                                    | 5432          | Postgres port                                             |
| DB_USERNAME                                       | postgres      | Postgres username                                         |
| DB_PASSWORD                                       | postgres      | Postgres password                                         |
| DB_DATABASE                                       | postgres      | Postgres database name                                    |
| MAX_QUERY_SIZE                                    | 1250000       | max result count per query                                |
| mod-fqm-manager.permissions-cache-timeout-seconds | 60            | Cache duration for user permissions                       |
| mod-fqm-manager.entity-type-cache-timeout-seconds | 3600          | Cache duration for entity type definitions                |
| server.port                                       | 8081          | Server port                                               |
| QUERY_RETENTION_DURATION                          | 3h            | Older queries get deleted                                 |
| task.execution.pool.core-size                     | 9             | Core number of concurrent async tasks                     |
| task.execution.pool.max-size                      | 10            | Max number of concurrent async tasks                      |
| task.execution.pool.queue-capacity                | 1000          | Size of the task queue                                    |

> **Note regarding `mod-fqm-manager.entity-type-cache-timeout-seconds`:** The default value defined in the project's
> module descriptor is `300`, as this value is more appropriate for development environments while still being reasonable
> for production. In general, production environments will see more benefit with higher values, since entity type
> definitions are very static and normally only change with module upgrades. `86400` (1 day) or `604800` (1 week) would
> be entirely reasonable for production. In the event that an entity type was updated in the database and this must be
> reflected quickly when the cache timeout is very high, simply restarting the module should be sufficient.

### Resource requirements

Most operations in the mod-fqm-module use very little memory. However, more memory is required when running queries
that return large amounts of data. Since mod-fqm-manager iterates over results in batches, sufficient memory is necessary
for good and consistent performance.With the default settings, you should allocate at least 1 gigabyte of heap space
to mod-fqm-manager to ensure optimal performance and query handling. For larger instances where mod-fqm-manager is heavily
used, allocate at least 2 gigabytes. In extreme cases with extremely large responses and concurrent usage, 5 gigabytes of
heap space should be sufficient to maintain performance and stability.

#### Local development only environment variables

> [!WARNING]
>
> These environment variables are intended for local development only and should not be used in production environments!

| Name                               | Default Value | Description                   |
| mod-fqm-manager.bypass-permissions | false         | Disable all permission checks |

## Installing the module
Follow the guide of Deploying Modules sections of the [Okapi Guide](https://github.com/folio-org/okapi/blob/master/doc/guide.md#example-1-deploying-and-using-a-simple-module) and Reference, which describe the process in detail.

First of all you need a running Okapi instance. (Note that specifying an explicit 'okapiurl' might be needed.)
```bash
   cd .../okapi
   java -jar okapi-core/target/okapi-core-fat.jar dev
```
We need to declare the module to Okapi:
```bash
  curl -w '\n' -X POST -D -   \
   -H "Content-type: application/json"   \
   -d @target/ModuleDescriptor.json \
   http://localhost:9130/_/proxy/modules
```
That ModuleDescriptor tells Okapi what the module is called, what services it provides, and how to deploy it.

## Deploying the module
Next we need to deploy the module. There is a deployment descriptor in target/DeploymentDescriptor.json. It tells Okapi to start the module on 'localhost'.

Deploy it via Okapi discovery:

```bash
   curl -w '\n' -D - -s \
  -X POST \
  -H "Content-type: application/json" \
  -d @target/DeploymentDescriptor.json  \
  http://localhost:9130/_/discovery/modules
```
Then we need to enable the module for the tenant:

```bash
  curl -w '\n' -X POST -D -   \
    -H "Content-type: application/json"   \
    -d @target/TenantModuleDescriptor.json \
    http://localhost:9130/_/proxy/tenants/<tenant_name>/modules
```

> **Note regarding ECS environments:** In ECS environments, FQM needs the central tenant ID when it is enabled, so FQM
> requests this data from mod-users. However, in new ECS environments, modules are enabled for a tenant before the tenant
> is added to a consortium, so mod-users is not able to provide this data at enable-time. As a result, in this case, it is
> necessary to provide the central tenant ID as a parameter, named `centralTenantId`, in the `POST` request to the tenant
> management API when enabling mod-fqm-manager. After the tenant has been added to the consortium, this parameter is no
> longer required when enabling mod-fqm-manager (e.g., when updating the module).


## Querying FQM - Step-by-step guide

#### Step 1: Identify the entity type for querying
An entity-type denotes a queryable relation in FQM.

You can obtain a list of all the entity types available by using the following API:

```bash
curl \
  -H 'Accept: application/json' \
  -H 'x-okapi-tenant: {{ tenant identifier }}' \
  -H 'x-okapi-token: {{ token }}' \
  -X GET {{ base-uri }}/entity-types
```

The response from the API will contain the ID and name of all available entity types.

#### Step 2: Identify the columns in an entity type

Each entity type encompasses a set of columns that are available for querying. To retrieve the available columns for an
entity type, you can make the following API call:

```bash
curl \
  -H 'Accept: application/json' \
  -H 'x-okapi-tenant: {{ tenant identifier }}' \
  -H 'x-okapi-token: {{ token }}' \
  -X GET {{ base-uri }}/entity-types/{{ entity type ID }}
```

The response will include an array called `columns`, where each column object contains a `name` representing the
column's name and a corresponding `dataType`. You can utilize the `name` field when constructing your query.

### Step 3: Construct the query
See [FQL](#FQL-(FQM-Query-Language)).

### Step 4: Execute the query
FQM offers two mechanisms to execute query
1. [Asynchronous query execution](#Executing-a-query-asynchronously)
2. [Synchronous query execution](#Executing-query-synchronously)

The asynchronous mechanism offers the advantage of providing the total count of results that match the query upfront.
This allows you to make an informed decision on whether to proceed with retrieving the query results or discard them if
the count does not meet your expectations.

On the other hand, in synchronous mode, FQM does not provide the results count upfront. Instead, you receive a stream of
results one page at a time. To determine the total count of results, you will need to count the results received in each
page until you have retrieved all the available data.

# Executing a query asynchronously
The workflow for asynchronous query execution is as follows: you begin by submitting the query to FQM and receiving a
queryId in return. Next, you periodically check the execution status using the queryId. Finally, once the query
execution is finished, you can initiate the retrieval of the query results.

The query results will be available for a duration of 3 hours. Once this time period elapses, the query results will be
permanently removed.

## Submit a query to FQM
The following API can be used to submit a query to FQM:

```bash
curl \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H 'x-okapi-tenant: {{ tenant identifier }}' \
  -H 'x-okapi-token: {{ token }}' \
  -X POST \
  -d '{ "fqlQuery": {{ your FQL query }}  , "entityTypeId": {{ The ID of the entity type }}' \
  {{ base-uri}}/query
```
The API will provide the identifier of the Query in its response.

## Check the status of a submitted query
The status of the submitted query can be obtained using the following API:

```bash
curl \
-H 'Accept: application/json' \
-H 'x-okapi-tenant: {{ tenant identifier }}' \
-H 'x-okapi-token: {{ token }}' \
-X GET {{ base-uri }}/query/{{ queryId }}
```

The response from the API will include the following fields:

- `status`: Indicates the current status of query execution. Possible values are IN_PROGRESS, SUCCESS, and FAILED.
- `totalRecords`: Represents the total number of records that match the query.

Optionally, if you include the query parameter `includeResults=true` in the API call, you will receive a preview of the
first 100 results.

## Retrieving query results
After the query execution status changes to SUCCESS, you can begin retrieving the results using the following API:

```bash
curl \
-H 'Accept: application/json' \
-H 'x-okapi-tenant: {{ tenant identifier }}' \
-H 'x-okapi-token: {{ token }}' \
-X GET {{ base-uri }}/query/{{ queryId }}?includeResults=true&offset={{ offset }}&limit={{ limit }}
```

To paginate through the results, you can utilize the optional `offset` and `limit` query parameters. It's important to
note that the offset parameter follows a zero-based index.

## Cancelling a query
To cancel a query after submission, you can utilize the following API:
```bash
curl \
-H 'Accept: application/json' \
-H 'x-okapi-tenant: {{ tenant identifier }}' \
-H 'x-okapi-token: {{ token }}' \
-X DELETE {{ base-uri }}/query/{{ queryId }}
```
Cancelling unnecessary queries is beneficial as it helps reduce the load on valuable system resources.

## Deleting query results
It is strongly advised to delete the query results once they are no longer required. You can accomplish this by
utilizing the following API:

```bash
curl \
-H 'Accept: application/json' \
-H 'x-okapi-tenant: {{ tenant identifier }}' \
-H 'x-okapi-token: {{ token }}' \
-X DELETE {{ base-uri }}/query/{{ queryId }}
```
Please note that query results will be automatically deleted after a period of 3 hours.

# Executing query synchronously
The process for executing the query synchronously involves the following steps:

1. Make an API call by providing the FQL query and specifying the maximum limit of results to be returned in the response.
2. Retrieve the first page of results from the API response.
3. Make subsequent API calls using the same query, the maximum record limit, and the ID of the last result obtained in
   the previous response.
4. Continue this process until you receive an empty results array, indicating that all desired results have been
   retrieved by paginating through the available data.

To retrieve the first page of results, you can use the following API:

```bash
curl \
-H 'Accept: application/json' \
-H 'x-okapi-tenant: {{ tenant identifier }}' \
-H 'x-okapi-token: {{ token }}' \
-X GET {{ base-uri }}/query?query={{ your query }}&entityTypeId={{ entityTypeId }}&limit={{ limit }}
```

To retrieve subsequent pages, you need to include an additional query parameter called `afterId`, which should be set to
the ID of the last result obtained in the previous page. This allows you to paginate through the results effectively.

# FQL (FQM Query Language)
FQL is the language for querying FQM. It is similar to the MongoDB query syntax but simpler.

## FQL operators
### $eq
Match values that are equal to a specified value. String comparison is done in a case-insensitive manner. Supports
string, number, boolean, date and uuid types.

Examples:
```json
{"field1": {"$eq": "value1"}}
```
```json
{"field1": {"$eq": 10}}
```
If a date in the "yyyy-mm-dd" format (i.e., without  time) is provided, the query should match all the records with that specific date, regardless of the time. The following date will match all records that match between Jan 10th 12:00 AM (including) and Jan 11th 12:00 AM (excluding)
```json
{"field1": {"$eq": "2023-01-10"}}
```
If a date and time are both provided, the query will match all the records with that specific date and time. The following date will match all records that match exactly at 2023-01-10 16:32:12 GMT
```json
{"field1": {"$eq": "2023-01-10T16:32:12Z"}}
```

### $ne
Match values that are not equal to a specified value. String comparison is done in a case-insensitive manner. Supports
string, number, boolean, date and uuid types.

Examples:
```json
{"field1": {"$ne": "value1"}}
```
```json
{"field1": {"$ne": 10}}
```
If a date in the "yyyy-mm-dd" format (i.e., without  time) is provided, the query should match all the records with that specific date, regardless of the time. The following date will match all the entries that don't have dates between Jan 10th 12:00 AM (including) and Jan 11th 12:00AM (excluding)
```json
{"field1": {"$ne": "2023-01-10"}}
```

### $gt
Match values that are greater than a specified value. Supports number and date types.

Examples:
```json
{"field1": {"$gt": "2023-01-12"}}
```

```json
{"field1": {"$gt": 10}}
```

### $gte
Match values that are greater than or equal to a specified value. Supports number and date types.

Examples:
```json
{"field1": {"$gte": "2023-01-12"}}
```

```json
{"field1": {"$gte": 10}}
```

### $lt
Match values that are less than a specified value. Supports number and date types.

Examples:
```json
{"field1": {"$lt": "2023-01-12"}}
```

```json
{"field1": {"$lt": 10}}
```

### $lte
Match values that are less than or equal to a specified value. Supports number and date types.

Examples:
```json
{"field1": {"$lte": "2023-01-12"}}
```

```json
{"field1": {"$lte": 10}}
```

### $in
Matches any of the values specified in an array. Supports array of string, date, number, uuid and boolean types.

Example:
```json
{"field1": {"$in": ["value1", "value2"]}}
```

### $nin
Matches none of the values specified in an array. Supports array of string, date, number, uuid and boolean types.

Example:
```json
{"field1": {"$nin": ["value1", "value2"]}}
```

### $contains_all
Matches all records where an array field contains all of the specified values. Supports an array of string, number, uuid, or boolean types.

Example:
```json
{"field1": {"$contains_all": [1, 2, 3]}}
```

### $not_contains_all
Matches all records where an array field does not contain all of the specified values. Supports an array of string, number, uuid, or boolean types.

Example:
```json
{"field1": {"$not_contains_all": [1, 2, 3]}}
```

### $contains_any
Matches all records where an array field contains any of the specified values. Supports an array of string, number, uuid, or boolean types.

Example:
```json
{"field1": {"$contains_any": [1, 2, 3]}}
```

### $not_contains_any
Matches all records where an array field does not contain any of the specified values. Supports an array of string, number, uuid, or boolean types.

Example:
```json
{"field1": {"$not_contains_any": [1, 2, 3]}}
```

### $empty
Can be true or false. If true, returns all records for which the specified column is null or empty. If false, returns all records for which the specified column is not null or empty.

Example:
```json
{"field1": {"$empty": true}}
```

### $regex
Provides regular expression capabilities for pattern matching strings in queries. At present, only the following two
patterns are supported.
- Starts with a given string. Example: ```/^comp```  will search for string starts with ```comp```
- Containing a given string. Example: ```comp```  will search for string containing ```comp```.

Examples:

Matches all records where ```field1``` contains the string ```test```:
```json
{"field1": {"$regex": "test"}}
```
Matches all records where ```field1``` starts with the string ```test```:
```json
{"field1": {"$regex": "/^test"}}
```

### $and
Joins query clauses with a logical AND. Return records that match all the conditions.
Example:
```json
{
    "$and": [
        {"field1": {"$regex": "test"}},
        {"field2": {"$in": ["value1", "value2"]}},
        {"field3": {"$eq": "value3"}}
    ]
}
```

## FQL reserved field names
Field names may be prefixed with special characters to indicate that the field is reserved for a specific purpose.

### Underscore (_)
Fields prefixed with an underscore are reserved for use by FQM.

#### _deleted
The field name "_deleted" can be used to indicate that the record corresponding to a particular content ID has been deleted.

#### _version
The name "_version" can be used to indicate the version number of the query.

## Additional information

### Issue tracker

See project [MODLISTS](https://issues.folio.org/browse/MODFQMMGR)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### Code of Conduct

Refer to the Wiki
[FOLIO Code of Conduct](https://wiki.folio.org/display/COMMUNITY/FOLIO+Code+of+Conduct).

### ModuleDescriptor

See the [ModuleDescriptor](descriptors/ModuleDescriptor-template.json)
for the interfaces that this module requires and provides, the permissions,
and the additional module metadata.

### API documentation

API descriptions:

* [OpenAPI](src/main/resources/swagger.api/mod-fqm-manager.yaml)
* [Schemas](src/main/resources/swagger.api/schemas/)

Generated [API documentation](https://dev.folio.org/reference/api/#mod-fqm-manager)

### Code analysis

[SonarQube analysis](https://sonarcloud.io/project/overview?id=org.folio%3Amod-fqm-manager)

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the Docker images for [released versions](https://hub.docker.com/r/folioorg/mod-fqm-manager/)
and for [snapshot versions](https://hub.docker.com/r/folioci/mod-fqm-manager/).

