import { EntityType, EntityTypeField } from '@/types';
import { Dialog, DialogTitle } from '@mui/material';
import { Schema } from 'genson-js/dist';
import { useState } from 'react';
import InitialImportConfig from './InitialImportConfig';
import FinishImport from './FinishImport';
import ImportStep from './ImportStep';

export const START_PAGE = -1;
export const END_PAGE = -2;

export interface State {
  page: number;
  source: string;
  schemaRaw: string;
  schema?: Schema & { properties: NonNullable<Schema['properties']> };
  columns: EntityTypeField[];
  translations: Record<string, string>;
  warnings: string[];
}

export default function JSONSchemaImporter({
  entityType,
  onImport,
  onClose,
}: Readonly<{
  entityType: EntityType;
  onImport: (columns: EntityTypeField[], newTranslations: Record<string, string>) => void;
  onClose: () => void;
}>) {
  const [state, setState] = useState<State>({
    page: START_PAGE,
    source: '',
    schemaRaw: '',
    columns: [],
    translations: {},
    warnings: [],
  });

  return (
    <Dialog open={true} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>Import from a JSON schema</DialogTitle>
      {state.page === START_PAGE ? (
        <InitialImportConfig entityType={entityType} state={state} setState={setState} onClose={onClose} />
      ) : state.page === END_PAGE ? (
        <FinishImport state={state} onImport={onImport} onClose={onClose} />
      ) : (
        <ImportStep entityType={entityType} state={state} setState={setState} onClose={onClose} />
      )}
    </Dialog>
  );
}
