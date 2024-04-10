import CheckValidity from '@/components/CheckValidity';
import EntityTypeManager from '@/components/EntityTypeManager';
import FqmConnector from '@/components/FqmConnector';
import PostgresConnector from '@/components/PostgresConnector';
import { EntityType } from '@/types';
import { Box, Container, Drawer, Tab, Tabs } from '@mui/material';
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
  const [selectedBottomTab, setSelectedBottomTab] = useState(0);
  const [expandedBottom, setExpandedBottom] = useState(false);

  const [currentEntityType, setCurrentEntityType] = useState<EntityType | null>(null);

  return (
    socket && (
      <Container>
        <Box sx={{ m: 2 }}>
          <FqmConnector socket={socket} />
          <PostgresConnector socket={socket} />
        </Box>

        <Box sx={{ mb: 8 }}>
          <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
            <Tabs value={selectedTab} onChange={(_e, n) => setSelectedTab(n)}>
              <Tab label="Entity Types" />
              <Tab label="DB Analyzer" />
            </Tabs>
          </Box>
          <Box sx={{ display: selectedTab === 0 ? 'block' : 'none' }}>
            <EntityTypeManager socket={socket} schema={schema} setCurrentEntityType={setCurrentEntityType} />
          </Box>
          <Box sx={{ display: selectedTab === 1 ? 'block' : 'none' }}>Item Two</Box>
        </Box>

        <Drawer
          sx={{
            height: selectedBottomTab ? (expandedBottom ? '60vh' : '30vh') : undefined,
            flexShrink: 0,
            '& .MuiDrawer-paper': {
              height: selectedBottomTab ? (expandedBottom ? '60vh' : '30vh') : undefined,
              boxSizing: 'border-box',
            },
          }}
          variant="permanent"
          anchor="bottom"
        >
          <Container>
            <Tabs
              value={selectedBottomTab}
              onChange={(_e, n) => {
                if (n === 3) {
                  setExpandedBottom((e) => !e);
                } else {
                  setSelectedBottomTab(n);
                }
              }}
              sx={{ borderTop: '1px solid #aaa' }}
            >
              <Tab label="Hide" disabled={selectedBottomTab === 0} />
              <Tab label="Check Validity" />
              <Tab label="DB Analyzer" />
              <Tab label={expandedBottom ? 'Collapse' : 'Expand'} />
            </Tabs>

            <Box sx={{ display: selectedBottomTab === 1 ? 'block' : 'none', p: 2 }}>
              <CheckValidity socket={socket} entityType={currentEntityType} />
            </Box>
          </Container>
        </Drawer>
      </Container>
    )
  );
}
