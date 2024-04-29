import { EntityType } from '@/types';
import { Autocomplete, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material';
import { useState } from 'react';

export default function NewEntityTypeDialog({
  entityTypes,
  onClose,
}: Readonly<{
  entityTypes: { file: string; data: EntityType }[];
  onClose: (newName: string | null) => void;
}>) {
  const [newName, setNewName] = useState('');
  return (
    <Dialog open={true} onClose={() => onClose(null)} maxWidth="md" fullWidth>
      <form onSubmit={(e) => onClose(newName)}>
        <DialogTitle>New entity type</DialogTitle>
        <DialogContent>
          <Autocomplete
            sx={{ mt: 2 }}
            freeSolo
            options={entityTypes.map((et) => et.file)}
            onChange={(_e, nv) => setNewName(nv ?? '')}
            renderInput={(params) => (
              <TextField
                {...params}
                label="Filename"
                required
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                helperText="Directories will be created as needed"
                inputProps={{ ...params.inputProps, pattern: '.+.json5', title: 'Include the .json5 suffix' }}
              />
            )}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => onClose(null)} color="error">
            Cancel
          </Button>
          <Button type="submit">Create</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
