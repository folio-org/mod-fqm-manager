# fqm-speed-comparison

This script runs a series of benchmarks against instance(s) of [mod-fqm-manager](https://github.com/folio-org/mod-fqm-manager) to compare speeds across versions and time.

## Requirements

- A running instance of mod-fqm-manager

## Usage

To benchmark, run the following (`QUERY_VERSION` refers to the index used in the `queries.json5` file):

```sh
USER=ABCDEF \
PASSWORD=123456 \
TENANT=fs09000000 \
OKAPI_URL=http://okapi:9130 \
QUERY_VERSION=1 \
LABEL=LABEL_HERE \
PG_HOST=... \
PG_PORT=... \
PG_DATABASE=... \
PG_USER=... \
PG_PASSWORD=... \
bun benchmark.ts
```

Once you've run this, `LABEL_HERE.json` and `LABEL_HERE-description.json` will have been created in the `raw-results` directory.

Once all datasets are ready, run:

```sh
bun chart.ts key1 key2 key3 ...
```

This will create a report `results.html`.
