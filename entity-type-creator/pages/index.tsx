import FqmConnector from '@/components/FqmConnector';
import { Box, Container } from '@mui/material';
import { useEffect, useState } from 'react';
import io, { Socket } from 'socket.io-client';

export default function EntryPoint() {
  const [socket, setSocket] = useState<Socket | null>(null);

  useEffect(() => {
    (async () => {
      if (!socket) {
        console.log('Creating socket');

        await fetch('/api/socket');
        const newSocket = io();

        newSocket.on('connect', () => {
          console.log('connected');
        });

        setSocket(newSocket);
      }
    })();
  }, []);

  return (
    socket && (
      <Container>
        <Box sx={{ m: 2 }}>
          <FqmConnector socket={socket} />
        </Box>
      </Container>
    )
  );
}
