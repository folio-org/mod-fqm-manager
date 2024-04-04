import verifyFqmConnection from '@/socket/verify-connection-fqm';
import verifyPostgresConnection from '@/socket/verify-connection-postgres';
import { FqmConnection, PostgresConnection } from '@/types';
import { Server } from 'Socket.IO';
import { NextApiRequest, NextApiResponse } from 'next';
import postgres from 'postgres';

let fqmConnection: FqmConnection;
let pg: postgres.Sql;

export default function SocketHandler(req: NextApiRequest, res: NextApiResponse<any>) {
  const socket = res.socket as any;

  console.log('Socket server is initializing');
  const io = new Server(socket?.server);
  socket.server.io = io;

  io.on('connection', (socket) => {
    console.log('connected!');

    socket.on('connect-to-fqm', async (params: FqmConnection) => {
      console.log('Connecting to FQM', params);

      socket.emit('fqm-connection-change', { connected: false, message: 'Attempting to connect...' });

      fqmConnection = params;

      socket.emit('fqm-connection-change', await verifyFqmConnection(fqmConnection));
    });

    socket.on('connect-to-postgres', async (params: PostgresConnection) => {
      console.log('Connecting to Postgres', params);

      socket.emit('postgres-connection-change', { connected: false, message: 'Attempting to connect...' });

      const result = await verifyPostgresConnection(params);
      pg = result.pg;

      socket.emit('postgres-connection-change', result.forClient);
    });
  });

  res.end();
}
