import { fetchEntityType, install, runQuery, uninstall, verifyFqmConnection } from '@/socket/fqm';
import {
  aggregateSchemaForAutocompletion,
  analyzeJsonb,
  persistEntityType,
  verifyPostgresConnection,
} from '@/socket/postgres';
import { EntityType, FqmConnection, PostgresConnection } from '@/types';
import formatEntityType, { fancyIndent } from '@/utils/formatter';
import dotenv from 'dotenv';
import json5 from 'json5';
import { NextApiRequest, NextApiResponse } from 'next';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import postgres from 'postgres';
import { Server } from 'socket.io';
import { v4 as uuid } from 'uuid';

export const ENTITY_TYPE_FILE_PATH = '../src/main/resources/entity-types/';

let fqmConnection: FqmConnection;
let pg: postgres.Sql | null = null;

export default function SocketHandler(req: NextApiRequest, res: NextApiResponse<any>) {
  const socket = res.socket as any;

  dotenv.config({ path: '../.env' });

  console.log('Socket server is initializing');
  const io = new Server(socket?.server);
  socket.server.io = io;

  io.on('connection', (socket) => {
    console.log('connected!');

    if ('DB_HOST' in process.env) {
      console.log('Found DB credentials in .env, sending up');
      socket.emit('db-credentials', {
        host: process.env.DB_HOST,
        port: process.env.DB_PORT,
        database: process.env.DB_DATABASE,
        user: process.env.DB_USERNAME,
        password: process.env.DB_PASSWORD,
      });
    }

    async function findEntityTypes() {
      console.log('Looking for entity types in', ENTITY_TYPE_FILE_PATH);
      const files = await Promise.all(
        (await readdir(ENTITY_TYPE_FILE_PATH, { recursive: true }))
          .filter((f) => f.endsWith('.json5'))
          .map(async (f) => {
            try {
              return { file: f, data: json5.parse((await readFile(ENTITY_TYPE_FILE_PATH + f)).toString()) };
            } catch (e) {
              console.error('Error reading entity type file', `${ENTITY_TYPE_FILE_PATH}${f}`, e);
              return { file: `⚠️⚠️⚠️${f}⚠️⚠️⚠️`, data: {} };
            }
          }),
      );

      console.log('Found', files.length, 'entity types');

      socket.emit('entity-types', files);
    }

    async function fetchDbSchema() {
      if (pg && fqmConnection) {
        socket.emit('database-schema', await aggregateSchemaForAutocompletion(pg, fqmConnection.tenant));
      }
    }

    socket.on('connect-to-fqm', async (params: FqmConnection) => {
      console.log('Connecting to FQM', params);

      socket.emit('fqm-connection-change', { connected: false, message: 'Attempting to connect...' });

      fqmConnection = params;

      socket.emit('fqm-connection-change', await verifyFqmConnection(fqmConnection));

      fetchDbSchema();
    });

    socket.on('connect-to-postgres', async (params: PostgresConnection) => {
      console.log('Connecting to Postgres', params);

      socket.emit('postgres-connection-change', { connected: false, message: 'Attempting to connect...' });

      const result = await verifyPostgresConnection(params);
      if (result.forClient.connected) pg = result.pg;

      socket.emit('postgres-connection-change', result.forClient);

      fetchDbSchema();
    });

    socket.on('enumerate-files', () => {
      findEntityTypes();
    });

    socket.on('get-translations', async () => {
      socket.emit('translations', JSON.parse((await readFile('../translations/mod-fqm-manager/en.json')).toString()));
    });

    socket.on('create-entity-type', async (name) => {
      console.log('Creating entity type', name);

      const dir = path.dirname(ENTITY_TYPE_FILE_PATH + name);
      console.log('Creating directory', dir);

      await mkdir(dir, { recursive: true });

      await writeFile(ENTITY_TYPE_FILE_PATH + name, json5.stringify({ id: uuid(), name: '' }, null, 2));

      findEntityTypes();
    });

    socket.on('save-entity-type', async ({ file, entityType }: { file: string; entityType: EntityType }) => {
      console.log('Saving entity type', file, entityType);

      await writeFile(
        ENTITY_TYPE_FILE_PATH + file,
        fancyIndent(json5.stringify(formatEntityType(entityType), null, 2)) + '\n',
      );

      socket.emit('saved-entity-type');

      findEntityTypes();
    });

    socket.on('refresh-entity-types', async () => {
      findEntityTypes();
      socket.emit('translations', JSON.parse((await readFile('../translations/mod-fqm-manager/en.json')).toString()));
    });

    socket.on('update-translations', async (newTranslations: Record<string, string>) => {
      if (Object.keys(newTranslations).length === 0) return;

      const curTranslations = JSON.parse((await readFile('../translations/mod-fqm-manager/en.json')).toString());

      const updatedTranslationSet = { ...curTranslations, ...newTranslations };
      const sorted = Object.keys(updatedTranslationSet)
        .toSorted()
        .reduce(
          (acc, key) => {
            acc[key] = updatedTranslationSet[key];
            return acc;
          },
          {} as Record<string, string>,
        );

      await writeFile('../translations/mod-fqm-manager/en.json', JSON.stringify(sorted, null, 2) + '\n');
      console.log('Updated translations');
      socket.emit('translations', sorted);
    });

    socket.on('check-entity-type-validity', async (entityType: EntityType) => {
      console.log('Checking entity type validity', entityType);

      if (!pg) {
        socket.emit('check-entity-type-validity-result', {
          persisted: false,
          queried: false,
          persistError: 'No Postgres connection',
        });
        return;
      }

      try {
        await persistEntityType(pg, fqmConnection.tenant, entityType);
        socket.emit('check-entity-type-validity-result', {
          persisted: true,
          queried: false,
        });
      } catch (e: any) {
        console.error('Error persisting entity type', e);
        socket.emit('check-entity-type-validity-result', {
          queried: false,
          persisted: false,
          persistError: e.message,
        });
        return;
      }

      try {
        socket.emit('check-entity-type-validity-result', {
          persisted: true,
          queried: true,
          queryResults: JSON.stringify(JSON.parse(await fetchEntityType(fqmConnection, entityType.id)), null, 2),
        });
      } catch (e: any) {
        console.error('Error fetching entity type', e);
        socket.emit('check-entity-type-validity-result', {
          persisted: true,
          queried: false,
          queryError: e.message,
        });
      }
    });

    socket.on('run-query', async ({ entityType, query }: { entityType: EntityType; query: string }) => {
      console.log('Running query', query, 'on', entityType.name);

      if (!pg) {
        socket.emit('run-query-result', {
          persisted: false,
          persistError: 'No Postgres connection',
        });
        return;
      }

      try {
        await persistEntityType(pg, fqmConnection.tenant, entityType);
        socket.emit('run-query-result', {
          persisted: true,
        });
      } catch (e: any) {
        console.error('Error persisting entity type', e);
        socket.emit('run-query-result', {
          persisted: false,
          persistError: e.message,
        });
        return;
      }

      try {
        socket.emit('run-query-result', {
          persisted: true,
          queryResults: await runQuery(fqmConnection, entityType, query),
        });
      } catch (e: any) {
        console.error('Error querying entity type', e);
        socket.emit('run-query-result', {
          persisted: true,
          queried: false,
          queryError: e.message,
        });
      }
    });

    socket.on('analyze-jsonb', ({ db, table, column }: { db: string; table: string; column: string }) => {
      analyzeJsonb(socket, pg!, fqmConnection.tenant, db, table, column);
    });

    socket.on('install-module', async () => socket.emit('install-module-result', await install(fqmConnection)));
    socket.on('uninstall-module', async () => socket.emit('uninstall-module-result', await uninstall(fqmConnection)));

    // ping-pong right back
    socket.on('add-column-from-db-inspector', (newColumn) =>
      socket.emit('add-column-from-db-inspector-pong', newColumn),
    );
  });

  res.end();
}
