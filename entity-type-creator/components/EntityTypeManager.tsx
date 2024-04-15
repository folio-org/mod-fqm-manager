import { Add } from '@mui/icons-material';
import { MenuItem, Select, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { Socket } from 'socket.io-client';
import { EntityType } from '../types';
import EntityTypeEditor from './EntityTypeEditor';
import NewEntityTypeDialog from './NewEntityTypeDialog';

export default function EntityTypeManager({
  schema,
  socket,
  setCurrentEntityType,
}: Readonly<{
  schema: Record<string, string[]>;
  socket: Socket;
  setCurrentEntityType: (n: EntityType | null) => void;
}>) {
  const [entityTypes, setEntityTypes] = useState<{ file: string; data: EntityType }[]>([]);
  const [translations, setTranslations] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<string>('');

  useEffect(() => {
    socket.emit('enumerate-files', '');
    socket.emit('get-translations', '');
  }, [socket]);

  useEffect(() => {
    socket.on('entity-types', (entityTypes) => {
      console.log('Received', entityTypes.length, 'entity types from socket');
      setEntityTypes(entityTypes);
    });

    socket.on('translations', (translations) => {
      console.log('Received', Object.keys(translations).length, 'translations from socket');
      setTranslations(translations);
    });
  }, []);

  const entityTypesFullList = entityTypes.map((et) => et.data);
  const selectedEntityType = entityTypes.find((et) => et.file === selected);

  return (
    <>
      <Typography sx={{ mt: 1, mb: 1 }}>
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
              setSelected('');
            } else {
              socket.emit('create-entity-type', newName);
              setSelected(newName);
            }
          }}
        />
      )}

      {selectedEntityType && (
        <EntityTypeEditor
          entityTypes={entityTypesFullList}
          entityType={selectedEntityType}
          setCurrentEntityType={setCurrentEntityType}
          translations={translations}
          schema={schema}
          socket={socket}
        />
      )}
    </>
  );
}
