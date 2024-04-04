import { Add } from '@mui/icons-material';
import { MenuItem, Select, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { Socket } from 'socket.io-client';
import { EntityType } from '../types';
import NewEntityTypeDialog from './NewEntityTypeDialog';

export default function EntityTypeManager({
  socket,
}: Readonly<{
  socket: Socket;
}>) {
  const [entityTypes, setEntityTypes] = useState<{ file: string; data: EntityType }[]>([]);
  const [selected, setSelected] = useState<string>('');

  useEffect(() => {
    socket.emit('enumerate-files', '');
  }, [socket]);

  useEffect(() => {
    socket.on('entity-types', (entityTypes) => {
      console.log('Received', entityTypes.length, 'from socket');
      setEntityTypes(entityTypes);
    });
  }, []);

  const selectedEntityType = entityTypes.find((et) => et.file === selected);

  return (
    <>
      <Typography>
        Editing:{' '}
        <Select size="small" value={selected} onChange={(e) => setSelected(e.target.value)}>
          <MenuItem key="new" value="new">
            <Add /> New entity type
          </MenuItem>
          {entityTypes
            .toSorted((a, b) => a.file.localeCompare(b.file))
            .map((et) => (
              <MenuItem key={et.file} value={et.file}>
                {et.file}
              </MenuItem>
            ))}
        </Select>
      </Typography>

      {selected === 'new' && (
        <NewEntityTypeDialog
          entityTypes={entityTypes}
          onClose={(newName) => {
            if (newName === null) {
              setSelected(newName);
            } else {
              socket.emit('create-entity-type', newName);
              setSelected(newName);
            }
          }}
        />
      )}
    </>
  );
}
