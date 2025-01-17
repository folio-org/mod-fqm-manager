import { writeFile } from 'fs';
import { parse, toSeconds } from 'iso8601-duration';
import json5 from 'json5';
import kyBase from 'ky-universal';
import postgres from 'postgres';
import { exit } from 'process';
import { promisify } from 'util';

const USERNAME = process.env.USER ?? 'folio';
const PASSWORD = process.env.PASSWORD ?? 'folio';
const TENANT = process.env.TENANT ?? 'fs09000000';
const OKAPI_URL = process.env.OKAPI_URL ?? 'http://localhost:9130';
const LABEL = process.env.LABEL ?? 'results';
const QUERY_VERSION = +(process.env.QUERY_VERSION ?? 0);

const pg = postgres({
  host: process.env.PG_HOST ?? 'localhost',
  port: +(process.env.PG_PORT ?? 5432),
  database: process.env.PG_DATABASE ?? 'folio',
  username: process.env.PG_USER ?? 'folio',
  password: process.env.PG_PASSWORD ?? '',
});

const resultsFile = `raw-results/${LABEL}.json`;
const descriptionsFile = `raw-results/${LABEL}-descriptions.json`;

async function auth(): Promise<string> {
  return (
    await kyBase.post(`${OKAPI_URL}/authn/login`, {
      headers: {
        'x-okapi-tenant': TENANT,
        'content-type': 'application/json',
      },
      json: {
        username: USERNAME,
        password: PASSWORD,
        tenant: TENANT,
      },
    })
  ).headers.get('x-okapi-token')!;
}

const ky = kyBase.extend({
  prefixUrl: OKAPI_URL,
  headers: {
    accept: 'application/json',
    'content-type': 'application/json',
    'x-okapi-tenant': TENANT,
    'x-okapi-token': await auth(),
  },
});

function getThisVersionOf<T>(arr: T[]): T {
  return arr[Math.min(QUERY_VERSION, arr.length - 1)];
}

async function getQueries(includeEachFieldsVariant: boolean): Promise<
  {
    label: string;
    entityTypeId: string;
    query: unknown;
    fields: string[];
  }[]
> {
  const queries: Record<
    string,
    {
      entityType: string[];
      queries: { label: string; queries: unknown[] }[];
      fields: { label: string; fields: string[][] }[];
    }
  > = await json5.parse(await Bun.file('queries.json5').text());

  const fullSet: {
    label: string;
    entityTypeId: string;
    query: unknown;
    fields: string[];
  }[] = [];

  Object.entries(queries).forEach(
    ([baseLabel, { entityType, queries, fields }]) => {
      const entityTypeId = getThisVersionOf(entityType);

      queries.forEach((queryList) =>
        fields
          .slice(0, includeEachFieldsVariant ? fields.length : 1)
          .forEach((fieldset) => {
            const label = [baseLabel, queryList.label, fieldset.label].join(
              '|'
            );

            const query = getThisVersionOf(queryList.queries);
            const queryFields = getThisVersionOf(fieldset.fields);

            if (!query || !queryFields || !entityTypeId) {
              return;
            }

            fullSet.push({
              label,
              entityTypeId,
              query,
              fields: queryFields,
            });
          })
      );
    }
  );

  return fullSet;
}

const queries = await getQueries(false);
const queriesWithFields = await getQueries(true);

let measures: Record<string, number[]> = {};
if (await Bun.file(resultsFile).exists()) {
  measures = JSON.parse(await Bun.file(resultsFile).text());
}
let descriptions: Record<string, string> = {};
if (await Bun.file(descriptionsFile).exists()) {
  descriptions = JSON.parse(await Bun.file(descriptionsFile).text());
}

async function save() {
  await promisify(writeFile)(resultsFile, JSON.stringify(measures));
  await promisify(writeFile)(descriptionsFile, JSON.stringify(descriptions));
}

async function measure<T, R>(
  name: string,
  run: (
    | {
        setup: () => Promise<T> | T;
        fn: (res: Awaited<T>) => Promise<R> | R;
        teardown: (res: Awaited<T>) => Promise<void> | void;
      }
    | {
        fn: () => Promise<R> | R;
      }
  ) & { describe: (r: R) => Promise<string> | string },
  maxTimeMs: number
) {
  measures[name] = measures[name] ?? [];

  process.stdout.write(`${name}: starting`);

  let res: Awaited<T> | undefined = undefined;
  if ('setup' in run) {
    process.stdout.clearLine(0);
    process.stdout.cursorTo(0);
    process.stdout.write(`${name}: setting up`);
    res = await run.setup();
    process.stdout.clearLine(0);
    process.stdout.cursorTo(0);
    process.stdout.write(`${name}: setup complete`);
  }

  let description: string | undefined = undefined;
  let overallStart = Date.now();
  let initialLength = measures[name].length;

  while (
    measures[name].length < 5 + initialLength ||
    Date.now() - overallStart < maxTimeMs
  ) {
    const start = Date.now();
    let result: R;
    if ('setup' in run) {
      result = await run.fn(res as Awaited<T>);
    } else {
      result = await run.fn();
    }
    const end = Date.now();

    if (description === undefined) {
      // should always be the same; only calculate it once
      description = await run.describe(result);
    }

    measures[name].push(end - start);
    process.stdout.clearLine(0);
    process.stdout.cursorTo(0);
    process.stdout.write(
      `${name} (${description}): ${
        measures[name].length - initialLength
      } samples, last ${end - start}ms`
    );
  }

  if ('teardown' in run) {
    await save();

    process.stdout.clearLine(0);
    process.stdout.cursorTo(0);
    process.stdout.write(`${name}: tearing down`);
    await run.teardown(res as Awaited<T>);
    process.stdout.clearLine(0);
    process.stdout.cursorTo(0);
    process.stdout.write(`${name}: teardown complete`);
  }

  descriptions[name.replace(/\|all$/, '')] = description ?? '';

  process.stdout.clearLine(0);
  process.stdout.cursorTo(0);
  console.log(
    `${name} (${description}): approx ${(
      measures[name].reduce((a, b) => a + b, 0) / measures[name].length
    ).toFixed(2)}ms (${measures[name].length} samples)`
  );

  await save();
}

async function measureEntityTypes() {
  const entityTypes = await ky
    .get('entity-types')
    .json<{ id: string; label: string }[]>();

  await measure(
    'get-all-entity-types',
    {
      fn: () => ky.get('entity-types'),
      describe: async (response) =>
        `${(await response.json<never[]>()).length} entities`,
    },
    5000
  );
  for (const { id, label } of entityTypes) {
    await measure(
      `get-entity-type-${label.toLowerCase()}`,
      {
        fn: () => ky.get(`entity-types/${id}`),
        describe: () => '',
      },
      5000
    );
  }
}

async function measureRefreshes() {
  async function waitForRefresh(listId: string) {
    return new Promise((resolve, reject) => {
      const checkIfDone = async () => {
        try {
          const response = await (await ky.get(`lists/${listId}`)).json<any>();
          if (
            response.successRefresh?.status === 'SUCCESS' &&
            !('inProgressRefresh' in response)
          ) {
            resolve(response.successRefresh.recordsCount);
          } else if ('failedRefresh' in response) {
            reject(new Error(JSON.stringify(response.failedRefresh, null, 2)));
          } else {
            setTimeout(checkIfDone, 50);
          }
        } catch (e) {
          console.error(e);
          setTimeout(checkIfDone, 0);
        }
      };
      checkIfDone();
    });
  }

  for (const { label, entityTypeId, query, fields } of queries) {
    // shouldn't need this...
    delete (query as { _version?: string })._version;

    await measure(
      label + '|all',
      {
        setup: async () => {
          const list = await (
            await ky.post('lists', {
              json: {
                name: `benchmark-${label}`,
                description: JSON.stringify(
                  { label, entityTypeId, query, fields },
                  null,
                  2
                ),
                entityTypeId,
                fqlQuery: JSON.stringify(query),
                fields,
                isActive: true,
                isPrivate: false,
              },
            })
          ).json<any>();

          return list;
        },
        fn: async (list) => {
          await ky.post(`lists/${list.id}/refresh`);

          return await waitForRefresh(list.id);
        },
        describe: (count) => `${count} records`,
        teardown: async (list) => {
          const refreshes = await pg`
            SELECT metadata
            FROM ${pg.unsafe(TENANT + '_mod_lists.list_refresh_details')}
            WHERE list_id = ${list.id} AND status = 'SUCCESS';`;

          for (const {
            metadata: { WAIT_FOR_QUERY_COMPLETION, IMPORT_RESULTS },
          } of refreshes) {
            measures[label + '|query'] = measures[label + '|query'] ?? [];
            measures[label + '|query'].push(
              toSeconds(parse(WAIT_FOR_QUERY_COMPLETION)) * 1000
            );

            measures[label + '|import-results'] =
              measures[label + '|import-results'] ?? [];
            measures[label + '|import-results'].push(
              toSeconds(parse(IMPORT_RESULTS)) * 1000
            );
          }

          await ky.delete(`lists/${list.id}`);
        },
      },
      60000
    );
  }
}

await measureEntityTypes();
// await measureRefreshes();
