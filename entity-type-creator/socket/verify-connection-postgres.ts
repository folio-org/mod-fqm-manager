import { PostgresConnection } from '@/types';
import postgres from 'postgres';

export default async function verifyPostgresConnection(postgresConnection: PostgresConnection) {
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
