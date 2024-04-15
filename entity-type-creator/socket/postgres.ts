import { EntityType, PostgresConnection } from '@/types';
import postgres from 'postgres';
import { mergeSchemas, createCompoundSchema, Schema } from 'genson-js';
import { Socket } from 'socket.io';

export async function verifyPostgresConnection(postgresConnection: PostgresConnection) {
  console.log('Attempting to connect to Postgres FQM connection', postgresConnection);

  const pg = postgres({
    host: postgresConnection.host,
    port: postgresConnection.port,
    database: postgresConnection.database,
    username: postgresConnection.user,
    password: postgresConnection.password,
  });

  try {
    await pg`SELECT 1`;
    return { pg, forClient: { connected: true, message: 'Connected!' } };
  } catch (e: any) {
    console.error('Unable to connect to Postgres', e);
    return { pg, forClient: { connected: false, message: `Unable to connect ${e.code} (${e.message})` } };
  }
}

export async function aggregateSchemaForAutocompletion(pg: postgres.Sql, tenant: string) {
  console.log('Aggregating schema for autocompletion with tenant ' + tenant);

  const columns = await pg`
    SELECT
      table_schema,
      table_name,
      table_type,
      column_name,
      data_type
    FROM
      information_schema.tables
      NATURAL JOIN information_schema.columns
    WHERE
      table_schema LIKE ${tenant + '_%'}
      AND table_schema NOT IN ('pg_catalog', 'information_schema');`;

  const routines = await pg`
    SELECT DISTINCT
      routine_schema,
      routine_name AS function_name
    FROM
      information_schema.routines
    WHERE
      routine_schema LIKE ${tenant + '_%'};`;

  console.log('found', columns.length, 'columns and', routines.length, 'routines');

  const schemaAggregated: Record<string, string[]> = {};
  const typeMapping: Record<string, string> = {};
  const isView: Record<string, boolean> = {};

  for (const { table_schema, table_name, table_type, column_name, data_type } of columns) {
    const schema = (table_schema as string).replace(`${tenant}_`, 'TENANT_');
    schemaAggregated[`${schema}.${table_name}`] = [...(schemaAggregated[`${schema}.${table_name}`] ?? []), column_name];
    typeMapping[`${schema}.${table_name}.${column_name}`] = data_type;
    isView[`${schema}.${table_name}`] = table_type === 'VIEW';
  }

  const routinesAggregated: Record<string, string[]> = {};
  for (const { routine_schema, function_name } of routines) {
    const schema = routine_schema.replace(`${tenant}_`, 'TENANT_');
    routinesAggregated[schema] = [...(routinesAggregated[schema] ?? []), function_name];
  }

  return { columns: schemaAggregated, routines: routinesAggregated, typeMapping, isView };
}

export async function persistEntityType(pg: postgres.Sql, tenant: string, entityType: EntityType) {
  console.log('Persisting entity type', entityType);

  // check if table entity_type_definition has matching ID for entityType.id
  const existing = await pg`
    SELECT id
    FROM ${pg.unsafe(tenant + '_mod_fqm_manager.entity_type_definition')}
    WHERE id = ${entityType.id};`;

  if (existing.length === 0) {
    console.log('Inserting new entity type', entityType.id);

    await pg`
      INSERT INTO ${pg.unsafe(tenant + '_mod_fqm_manager.entity_type_definition')}
      (id, definition)
      VALUES (${entityType.id}, ${pg.json(entityType as any)});`;
  } else {
    await pg`
      UPDATE ${pg.unsafe(tenant + '_mod_fqm_manager.entity_type_definition')}
      SET definition = ${pg.json(entityType as any)}
      WHERE id = ${entityType.id};`;
  }
}

export async function analyzeJsonb(
  socket: Socket,
  pg: postgres.Sql,
  tenant: string,
  db: string,
  table: string,
  column: string,
) {
  console.log('Analyzing JSONB structure of', db, table, column);

  const total = (await pg`SELECT COUNT(1) FROM ${pg.unsafe(`${db.replaceAll('TENANT', tenant)}.${table}`)}`)[0].count;
  console.log('Found', total, 'records to analyze');

  socket.emit(`analyze-jsonb-result-${db}-${table}-${column}`, { scanned: 0, total, finished: false });

  let aborted = false;

  let schema: Schema | null = null;

  let done = 0;

  for (let scanned = 0; scanned < total && !aborted; scanned += 100) {
    console.log('Scanned', scanned, 'of', total, 'records');
    const query = pg`SELECT ${pg.unsafe(column)} FROM ${pg.unsafe(
      `${db.replaceAll('TENANT', tenant)}.${table}`,
    )} LIMIT 100 OFFSET ${scanned}`;

    socket.removeAllListeners(`abort-analyze-jsonb-${db}-${table}-${column}`);
    socket.once(`abort-analyze-jsonb-${db}-${table}-${column}`, () => {
      console.log('Aborting analysis of', db, table, column);

      aborted = true;
      query.cancel();
    });

    const rows = await query;

    if (!aborted) {
      const jsons = rows.map((row) => row[column]);

      const thisBatch = createCompoundSchema(jsons);
      schema = schema ? mergeSchemas([schema, thisBatch]) : thisBatch;

      done = Math.min(total, scanned + 100);

      socket.emit(`analyze-jsonb-result-${db}-${table}-${column}`, {
        scanned: done,
        total,
        finished: false,
      });
    }
  }

  if (schema) {
    socket.emit(`analyze-jsonb-result-${db}-${table}-${column}`, {
      scanned: done,
      total,
      finished: true,
      result: schema,
    });
  }
}
