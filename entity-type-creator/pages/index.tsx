import EntityTypeManager from '@/components/EntityTypeManager';
import FqmConnector from '@/components/FqmConnector';
import PostgresConnector from '@/components/PostgresConnector';
import { Box, Button, Container, Tab, Tabs } from '@mui/material';
import { useEffect, useState } from 'react';
import io, { Socket } from 'socket.io-client';

export default function EntryPoint() {
  const [socket, setSocket] = useState<Socket | null>(null);
  const [schema, setSchema] = useState<Record<string, string[]>>({});

  useEffect(() => {
    (async () => {
      if (!socket) {
        console.log('Creating socket');

        await fetch('/api/socket');
        const newSocket = io();

        newSocket.on('connect', () => {
          console.log('connected');
        });

        newSocket.on('database-schema', (schema) => {
          console.log('Received schema', schema);
          setSchema(schema);
        });

        setSocket(newSocket);
      }
    })();
  }, [socket]);

  const [selectedTab, setSelectedTab] = useState(0);

  return (
    socket && (
      <Container>
        <Box sx={{ m: 2 }}>
          <FqmConnector socket={socket} />
          <PostgresConnector socket={socket} />
        </Box>

        <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tabs value={selectedTab} onChange={(_e, n) => setSelectedTab(n)}>
            <Tab label="Entity Types" />
            <Tab label="DB Analyzer" />
          </Tabs>
        </Box>
        <Box sx={{ display: selectedTab === 0 ? 'block' : 'none' }}>
          <EntityTypeManager socket={socket} schema={schema} />
        </Box>
        <Box sx={{ display: selectedTab === 1 ? 'block' : 'none' }}>Item Two</Box>
      </Container>
    )
  );
}
