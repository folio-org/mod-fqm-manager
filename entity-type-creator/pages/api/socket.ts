import verifyFqmConnection from '@/socket/verify-connection-fqm';
import { FqmConnection } from '@/types';
import { Server } from 'Socket.IO';
import { NextApiRequest, NextApiResponse } from 'next';

let fqmConnection: FqmConnection;

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
  });

  res.end();
}
