import { PostgresConnection } from '@/types';
import postgres from 'postgres';

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
      column_name
    FROM
      information_schema.tables
      NATURAL JOIN information_schema.columns
    WHERE
      table_schema LIKE ${tenant + '_%'}
      AND table_schema NOT IN ('pg_catalog', 'information_schema');`;

  const routines = await pg`
    SELECT
      routine_schema,
      routine_name AS function_name
    FROM
      information_schema.routines
    WHERE
      routine_schema LIKE ${tenant + '_%'};`;

  console.log('found', columns.length, 'columns and', routines.length, 'routines');

  const schemaAggregated: Record<string, string[]> = {};
  for (const { table_schema, table_name, column_name } of columns) {
    const schema = (table_schema as string).replace(`${tenant}_`, 'TENANT_');
    schemaAggregated[`${schema}.${table_name}`] = [...(schemaAggregated[`${schema}.${table_name}`] ?? []), column_name];
  }

  const routinesAggregated: Record<string, string[]> = {};
  for (const { routine_schema, function_name } of routines) {
    const schema = routine_schema.replace(`${tenant}_`, 'TENANT_');
    routinesAggregated[schema] = [...(routinesAggregated[schema] ?? []), function_name];
  }

  return { ...schemaAggregated, ...routinesAggregated };
}
