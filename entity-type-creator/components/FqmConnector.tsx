import { Accordion, AccordionDetails, AccordionSummary, Button, Grid, TextField } from '@mui/material';
import { useEffect, useState } from 'react';
import { Socket } from 'socket.io-client';
import { FqmConnection } from '../types';

export default function FqmConnector({
  socket,
}: Readonly<{
  socket: Socket;
}>) {
  const [open, setOpen] = useState(true);

  const [fqmConnection, setFqmConnection] = useState<FqmConnection>({
    host: 'localhost',
    port: 8081,
    tenant: 'fs09000000',
    limit: 100,
  });
  const [connectionState, setConnectionState] = useState({ connected: false, message: 'Waiting to connect...' });

  useEffect(() => {
    socket.on('fqm-connection-change', (msg) => {
      console.log('FQM Connection change', msg);
      setConnectionState(msg);
      if (msg.connected) {
        setOpen(false);
      }
    });
  }, []);

  return (
    <Accordion expanded={open} onChange={() => setOpen((o) => !o)}>
      <AccordionSummary>
        <span>
          FQM Connection{' '}
          <span style={{ fontFamily: 'monospace', marginLeft: '1ch' }}>
            {fqmConnection.host}:{fqmConnection.port} ({fqmConnection.tenant})
          </span>
          <br />
          <span style={{ color: connectionState.connected ? 'green' : 'red' }}>{connectionState.message}</span>
        </span>
      </AccordionSummary>
      <AccordionDetails>
        <form
          onSubmit={(e) => {
            e.preventDefault();

            socket.emit('connect-to-fqm', fqmConnection);

            return false;
          }}
        >
          <Grid container spacing={2} sx={{ alignItems: 'center' }}>
            <Grid item xs={4}>
              <TextField
                label="Host"
                fullWidth
                value={fqmConnection.host}
                onChange={(e) => setFqmConnection({ ...fqmConnection, host: e.target.value })}
                required
              />
            </Grid>
            <Grid item xs={2}>
              <TextField
                label="Port"
                type="number"
                fullWidth
                inputProps={{ min: 0, max: 65535, step: 1 }}
                value={fqmConnection.port}
                onChange={(e) => setFqmConnection({ ...fqmConnection, port: parseInt(e.target.value) })}
                required
              />
            </Grid>
            <Grid item xs={2}>
              <TextField
                label="Tenant"
                fullWidth
                value={fqmConnection.tenant}
                onChange={(e) => setFqmConnection({ ...fqmConnection, tenant: e.target.value })}
                required
              />
            </Grid>
            <Grid item xs={2}>
              <TextField
                label="Fetch limit"
                type="number"
                fullWidth
                inputProps={{ min: 1, step: 1 }}
                value={fqmConnection.limit}
                onChange={(e) => setFqmConnection({ ...fqmConnection, limit: parseInt(e.target.value) })}
                required
              />
            </Grid>
            <Grid item xs={2}>
              <Button variant="outlined" type="submit" size="large" fullWidth>
                connect
              </Button>
            </Grid>
          </Grid>
        </form>
      </AccordionDetails>
    </Accordion>
  );
}
