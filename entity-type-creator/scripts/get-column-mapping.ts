import { FqmConnection } from '@/types';
import { readdir } from 'node:fs/promises';
import { parse } from 'papaparse';
import { ResultRowPretty } from './dump-entity-type-information';
import { migrate } from '@/socket/fqm';

export interface Mapping {
  src: { entityType: string; column: string };
  dest: { entityType: string; column: string };
  warnings: string[];
}

if (process.argv.length < 4) {
  console.error('Usage: bun scripts/compare-across-time.ts <start-version> <start>');
  process.exit(1);
}

const FQM_CONNECTION: FqmConnection = {
  host: process.env.FQM_HOST!,
  port: parseInt(process.env.FQM_PORT ?? '8080'),
  tenant: process.env.FQM_TENANT!,
  limit: 50,
  user: process.env.FQM_USERNAME,
  password: process.env.FQM_PASSWORD,
};

async function readAllRows(dir: string) {
  const files = await readdir(`./dump/${dir}`);
  const rows: ResultRowPretty[] = [];
  for (const file of files) {
    rows.push(...parse<ResultRowPretty>(await Bun.file(`./dump/${dir}/${file}`).text(), { header: true }).data);
  }
  return rows;
}

const srcRows = await readAllRows(process.argv[3]);

const result: Mapping[] = [];

for (const row of srcRows) {
  const migrated = await migrate(
    FQM_CONNECTION,
    row['Entity ID'],
    `{"_version": "${process.argv[2]}", "${row.Name}": {}}`,
    [row.Name],
  );
  if (migrated.entityTypeId !== row['Entity ID'] || migrated.fields[0] !== row.Name) {
    result.push({
      src: {
        entityType: row['Entity ID'],
        column: row.Name,
      },
      dest: {
        entityType: migrated.entityTypeId,
        column: migrated.fields[0],
      },
      warnings: migrated.warnings.map((w) => w.description),
    });
  }
}

await Bun.write(`./dump/${process.argv[3]}-mapping.json`, JSON.stringify(result, null, 2));
