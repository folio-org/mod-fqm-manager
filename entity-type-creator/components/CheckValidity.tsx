import { Done, Error, Pending, Schedule } from '@mui/icons-material';
import { Button, Typography } from '@mui/material';
import { useCallback, useState } from 'react';
import { Socket } from 'socket.io-client';
import { EntityType } from '../types';

enum State {
  NOT_STARTED,
  STARTED,
  PERSISTED,
  DONE,
  ERROR_PERSIST,
  ERROR_QUERY,
}

export default function CheckValidity({
  socket,
  entityType,
}: Readonly<{
  socket: Socket;
  entityType: EntityType | null;
}>) {
  const [state, setState] = useState<{ state: State; result?: string }>({ state: State.NOT_STARTED });

  const run = useCallback(
    (entityType: EntityType) => {
      setState({ state: State.STARTED });

      socket.emit('check-entity-type-validity', entityType);
      socket.on(
        'check-entity-type-validity-result',
        (result: {
          queried: boolean;
          persisted: boolean;
          queriedError?: string;
          persistedError?: string;
          queryResults?: string;
        }) => {
          if (result.queriedError || result.persistedError || result.queryResults) {
            socket.off('check-entity-type-validity-result');
          }

          if (result.queriedError) {
            setState({ state: State.ERROR_QUERY, result: result.queriedError });
          } else if (result.persistedError) {
            setState({ state: State.ERROR_PERSIST, result: result.persistedError });
          } else if (result.queryResults) {
            setState({ state: State.DONE, result: result.queryResults });
          } else {
            setState({ state: State.PERSISTED });
          }
        }
      );
    },
    [state, socket]
  );

  if (entityType === null) {
    return null;
  }

  return (
    <>
      <Typography>
        Checks that <code>mod-fqm-manager</code> can successfully parse and handle the JSON representation of the entity
        type.
      </Typography>

      <Button variant="outlined" onClick={() => run(entityType)}>
        Run
      </Button>

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
        Query <code>/entity-types/{entityType.id}</code>
      </Typography>

      {!!state.result && <pre>{state.result}</pre>}
    </>
  );
}
