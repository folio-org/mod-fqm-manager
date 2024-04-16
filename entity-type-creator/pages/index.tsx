import CheckValidity from '@/components/CheckValidity';
import DBInspector from '@/components/DBInspector';
import EntityTypeManager from '@/components/EntityTypeManager';
import FqmConnector from '@/components/FqmConnector';
import ModuleInstaller from '@/components/ModuleInstaller';
import PostgresConnector from '@/components/PostgresConnector';
import QueryTool from '@/components/QueryTool';
import { EntityType, Schema } from '@/types';
import { Box, Container, Drawer, Tab, Tabs } from '@mui/material';
import { useEffect, useState } from 'react';
import io, { Socket } from 'socket.io-client';

export default function EntryPoint() {
  const [socket, setSocket] = useState<Socket | null>(null);
  const [schema, setSchema] = useState<Schema>({ columns: {}, routines: {}, typeMapping: {}, isView: {} });

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
          <EntityTypeManager
            socket={socket}
            schema={{ ...schema.columns, ...schema.routines }}
            setCurrentEntityType={setCurrentEntityType}
          />
        </Box>

        <Drawer
          sx={{
            height: selectedTab ? (expandedBottom ? '60vh' : '30vh') : undefined,
            flexShrink: 0,
            '& .MuiDrawer-paper': {
              height: selectedTab ? (expandedBottom ? '60vh' : '30vh') : undefined,
              boxSizing: 'border-box',
            },
          }}
          variant="permanent"
          anchor="bottom"
        >
          <Container>
            <Tabs
              value={selectedTab}
              onChange={(_e, n) => {
                if (n === 5) {
                  setExpandedBottom((e) => !e);
                } else {
                  setSelectedTab(n);
                }
              }}
              sx={{ borderTop: '1px solid #aaa' }}
            >
              <Tab label="Hide" disabled={selectedTab === 0} />
              <Tab label="Module Installer" />
              <Tab label="Check Validity" />
              <Tab label="Query Tool" />
              <Tab label="DB Inspector" />
              <Tab label={expandedBottom ? 'Collapse' : 'Expand'} />
            </Tabs>

            <Box sx={{ display: selectedTab === 1 ? 'block' : 'none', p: 2 }}>
              <ModuleInstaller socket={socket} />
            </Box>
            <Box sx={{ display: selectedTab === 2 ? 'block' : 'none', p: 2 }}>
              <CheckValidity socket={socket} entityType={currentEntityType} />
            </Box>
            <Box sx={{ display: selectedTab === 3 ? 'block' : 'none', p: 2 }}>
              <QueryTool socket={socket} entityType={currentEntityType} />
            </Box>
            <Box sx={{ display: selectedTab === 4 ? 'block' : 'none', p: 2 }}>
              <DBInspector socket={socket} schema={schema} />
            </Box>
          </Container>
        </Drawer>
      </Container>
    )
  );
}
