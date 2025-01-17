import { EntityTypeField } from '@/types';
import { Alert, Button, DialogActions, DialogContent, Typography } from '@mui/material';
import { State } from './JSONSchemaImporter';

export default function FinishImport({
  state,
  onImport,
  onClose,
}: Readonly<{
  state: State;
  onImport: (columns: EntityTypeField[], newTranslations: Record<string, string>) => void;
  onClose: () => void;
}>) {
  return (
    <>
      <DialogContent>
        <Typography>Import summary</Typography>

        <Alert severity="info">
          <Typography>
            The following {state.columns.length} columns will be imported:
            <ul>
              {state.columns.map((column) => (
                <li key={column.name}>{column.name}</li>
              ))}
            </ul>
          </Typography>
        </Alert>

        {!!state.warnings.length && (
          <Alert severity="error">
            <Typography>
              The following issues were encountered; be sure to take note of these so you can resolve them afterwards!
              <ul>
                {state.warnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            </Typography>
          </Alert>
        )}

        <Typography>To complete your import, press "Finish" below.</Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} color="error">
          Cancel
        </Button>
        <Button
          onClick={() => {
            onImport(state.columns, state.translations);
            onClose();
          }}
        >
          Finish
        </Button>
      </DialogActions>
    </>
  );
}
