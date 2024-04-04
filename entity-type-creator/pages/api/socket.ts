import { verifyFqmConnection } from '@/socket/fqm';
import { aggregateSchemaForAutocompletion, verifyPostgresConnection } from '@/socket/postgres';
import { EntityType, FqmConnection, PostgresConnection } from '@/types';
import { Server } from 'Socket.IO';
import json5 from 'json5';
import { NextApiRequest, NextApiResponse } from 'next';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import postgres from 'postgres';
import { v4 as uuid } from 'uuid';

export const ENTITY_TYPE_FILE_PATH = '../src/main/resources/entity-types/';

let fqmConnection: FqmConnection;
let pg: postgres.Sql | null = null;

export default function SocketHandler(req: NextApiRequest, res: NextApiResponse<any>) {
  const socket = res.socket as any;

  console.log('Socket server is initializing');
  const io = new Server(socket?.server);
  socket.server.io = io;

  io.on('connection', (socket) => {
    console.log('connected!');

    async function findEntityTypes() {
      console.log('Looking for entity types in', ENTITY_TYPE_FILE_PATH);
      const files = await Promise.all(
        (await readdir(ENTITY_TYPE_FILE_PATH, { recursive: true }))
          .filter((f) => f.endsWith('.json5'))
          .map(async (f) => ({ file: f, data: json5.parse((await readFile(ENTITY_TYPE_FILE_PATH + f)).toString()) }))
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

      // todo: sorting
      await writeFile(ENTITY_TYPE_FILE_PATH + file, json5.stringify(entityType, null, 2));

      socket.emit('saved-entity-type');

      findEntityTypes();
    });
  });

  res.end();
}
