import { Done, Error, Pending, Schedule } from '@mui/icons-material';
import { Button, Typography } from '@mui/material';
import { useCallback, useMemo, useState } from 'react';
import { Socket } from 'socket.io-client';
import { EntityType } from '../types';
import { json } from '@codemirror/lang-json';
import CodeMirror from '@uiw/react-codemirror';
import JSONTable from './JSONTable';

export default function ModuleInstaller({
  socket,
}: Readonly<{
  socket: Socket;
}>) {
  const [state, setState] = useState<{ installing: boolean; removing: boolean; result?: string }>({
    installing: false,
    removing: false,
  });

  const install = useCallback(() => {
    setState({ installing: true, removing: false });

    socket.emit('install-module');
    socket.on('install-module-result', (result: { status: string; body: string }) => {
      socket.off('install-module-result');

      setState({ installing: false, removing: false, result: result.status + '\n' + result.body });
    });
  }, [state, socket]);

  const uninstall = useCallback(() => {
    setState({ installing: false, removing: true });

    socket.emit('uninstall-module');
    socket.on('uninstall-module-result', (result: { status: string; body: string }) => {
      socket.off('uninstall-module-result');

      setState({ installing: false, removing: false, result: result.status + '\n' + result.body });
    });
  }, [state, socket]);

  return (
    <>
      <Typography>
        Installs or uninstalls the module (via <code>/_/tenant</code>).
      </Typography>

      <Button variant="outlined" color="success" onClick={install} disabled={state.installing}>
        install
      </Button>
      <Button variant="outlined" color="error" onClick={uninstall} disabled={state.removing} sx={{ ml: 2 }}>
        uninstall
      </Button>

      {!!state.result && <pre>{state.result}</pre>}
    </>
  );
}
