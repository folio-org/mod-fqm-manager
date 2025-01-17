import { Done, Error, Pending, Schedule } from '@mui/icons-material';
import { Autocomplete, Box, Button, TextField, Typography } from '@mui/material';
import { useCallback, useState } from 'react';
import { Socket } from 'socket.io-client';
import { EntityType } from '../types';
import JSONTable from './JSONTable';

enum State {
  NOT_STARTED,
  STARTED,
  PERSISTED,
  DONE,
  ERROR_PERSIST,
  ERROR_QUERY,
}

export default function QueryValues({
  socket,
  entityType,
}: Readonly<{
  socket: Socket;
  entityType: EntityType | null;
}>) {
  const [started, setStarted] = useState(new Date().getTime());
  const [ended, setEnded] = useState(new Date().getTime());
  const [state, setState] = useState<{ state: State; result?: { value: string; label?: string }[] | string }>({
    state: State.NOT_STARTED,
  });

  const [field, setField] = useState<string>('id');

  const run = useCallback(
    (entityType: EntityType, field: string) => {
      setState({ state: State.STARTED });

      socket.emit('run-query-values', { entityType, field });
      socket.on(
        'run-query-values-result',
        (result: {
          persisted?: boolean;
          queryError?: string;
          persistError?: string;
          queryResults?: { value: string; label?: string }[];
        }) => {
          if (result.queryError || result.persistError || result.queryResults) {
            socket.off('run-query-values-result');
          }

          if (result.queryError) {
            setState({ state: State.ERROR_QUERY, result: result.queryError });
          } else if (result.persistError) {
            setState({ state: State.ERROR_PERSIST, result: result.persistError });
          } else if (result.queryResults) {
            setState({ state: State.DONE, result: result.queryResults });
            console.log(result.queryResults);
            setEnded(new Date().getTime());
          } else {
            setState({ state: State.PERSISTED });
            setStarted(new Date().getTime());
          }
        },
      );
    },
    [state, socket],
  );

  if (entityType === null) {
    return <p>Select an entity type first</p>;
  }

  return (
    <>
      <Typography>Queries values for a field.</Typography>

      <Box sx={{ display: 'flex', m: 2, gap: '1em' }}>
        <Autocomplete
          freeSolo
          options={entityType.columns?.map((c) => c.name) ?? []}
          value={field}
          onChange={(_e, nv) => setField(nv ?? '')}
          sx={{ fontFamily: 'monospace' }}
          renderInput={(params) => (
            <TextField
              {...params}
              sx={{ width: '60ch', fontFamily: 'monospace' }}
              onChange={(e) => setField((e.currentTarget as HTMLInputElement).value)}
              size="small"
              label="Field"
              required
            />
          )}
        />

        <Button variant="outlined" onClick={() => run(entityType, field)}>
          Run
        </Button>
      </Box>

      <Typography sx={{ display: 'flex', alignItems: 'center', gap: '0.5em', m: 2 }}>
        {state.state === State.NOT_STARTED ? (
          <Pending color="disabled" />
        ) : state.state === State.STARTED ? (
          <Schedule color="warning" />
        ) : state.state === State.ERROR_PERSIST ? (
          <Error color="error" />
        ) : (
          <Done color="success" />
        )}
        Persist to database
      </Typography>
      <Typography sx={{ display: 'flex', alignItems: 'center', gap: '0.5em', m: 2 }}>
        {state.state === State.NOT_STARTED || state.state === State.STARTED ? (
          <Pending color="disabled" />
        ) : state.state === State.PERSISTED ? (
          <Schedule color="warning" />
        ) : state.state === State.ERROR_PERSIST || state.state === State.ERROR_QUERY ? (
          <Error color="error" />
        ) : (
          <Done color="success" />
        )}{' '}
        Run query {state.state === State.DONE ? `(${(ended - started) / 1000}s)` : ''}
      </Typography>
      {!!state.result &&
        (typeof state.result === 'string' ? <pre>{state.result}</pre> : <JSONTable data={state.result} />)}
    </>
  );
}
