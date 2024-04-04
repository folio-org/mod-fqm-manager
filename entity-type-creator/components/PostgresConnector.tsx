import { Accordion, AccordionDetails, AccordionSummary, Button, Grid, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { Socket } from 'socket.io-client';
import { PostgresConnection } from '../types';

export default function PostgresConnector({
  socket,
}: Readonly<{
  socket: Socket;
}>) {
  const [open, setOpen] = useState(true);

  const [postgresConnection, setPostgresConnection] = useState<PostgresConnection>({
    host: 'localhost',
    port: 5432,
    database: 'db',
    user: 'postgres',
    password: 'postgres',
  });
  const [connectionState, setConnectionState] = useState({ connected: false, message: 'Waiting to connect...' });

  useEffect(() => {
    socket.on('postgres-connection-change', (msg) => {
      console.log('Postgres Connection change', msg);
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
          Postgres Connection{' '}
          <span style={{ fontFamily: 'monospace', marginLeft: '1ch' }}>
            {postgresConnection.host}:{postgresConnection.port} ({postgresConnection.database})
          </span>
          <br />
          <span style={{ color: connectionState.connected ? 'green' : 'red' }}>{connectionState.message}</span>
        </span>
      </AccordionSummary>
      <AccordionDetails>
        <form
          onSubmit={(e) => {
            e.preventDefault();

            socket.emit('connect-to-postgres', postgresConnection);

            return false;
          }}
        >
          <Grid container spacing={2} sx={{ alignItems: 'center' }}>
            <Grid item xs={2}>
              <TextField
                label="Host"
                fullWidth
                value={postgresConnection.host}
                onChange={(e) => setPostgresConnection({ ...postgresConnection, host: e.target.value })}
                required
              />
            </Grid>
            <Grid item xs={2}>
              <TextField
                label="Port"
                type="number"
                fullWidth
                inputProps={{ min: 0, max: 65535, step: 1 }}
                value={postgresConnection.port}
                onChange={(e) => setPostgresConnection({ ...postgresConnection, port: parseInt(e.target.value) })}
                required
              />
            </Grid>
            <Grid item xs={2}>
              <TextField
                label="Database"
                fullWidth
                value={postgresConnection.database}
                onChange={(e) => setPostgresConnection({ ...postgresConnection, database: e.target.value })}
                required
              />
            </Grid>
            <Grid item xs={2}>
              <TextField
                label="Username"
                fullWidth
                value={postgresConnection.user}
                onChange={(e) => setPostgresConnection({ ...postgresConnection, user: e.target.value })}
                required
              />
            </Grid>
            <Grid item xs={2}>
              <TextField
                label="Password"
                type="password"
                fullWidth
                value={postgresConnection.password}
                onChange={(e) => setPostgresConnection({ ...postgresConnection, password: e.target.value })}
                required
              />
            </Grid>
            <Grid item xs={2}>
              <Button variant="outlined" type="submit" size="large" fullWidth>
                connect
              </Button>
            </Grid>
          </Grid>
          <Typography>
            <i>
              If you need to access a remote database via a jumpbox, use SSH port forwarding locally to expose it
              locally (e.g. on port 5433):
            </i>
            <br />
            <code>ssh -NL 5433:remote-db-server:5432 user@jumpbox</code>
          </Typography>
        </form>
      </AccordionDetails>
    </Accordion>
  );
}
