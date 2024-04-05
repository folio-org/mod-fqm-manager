import { PostgreSQL, sql } from '@codemirror/lang-sql';
import { StreamLanguage } from '@codemirror/language';
import { Refresh } from '@mui/icons-material';
import { Alert, Button, Checkbox, FormControlLabel, Grid, IconButton, InputAdornment, TextField } from '@mui/material';
import CodeMirror from '@uiw/react-codemirror';
import { useEffect, useMemo, useState } from 'react';
import { Socket } from 'socket.io-client';
import { v4 as uuid } from 'uuid';
import { EntityType } from '../types';
import { formatSql } from '@/utils/sqlUtils';

export default function EntityTypeManager({
  entityTypes,
  entityType: { file, data: initialValues },
  translations,
  schema,
  socket,
}: Readonly<{
  entityTypes: Pick<EntityType, 'id' | 'name'>[];
  entityType: { file: string; data: EntityType };
  translations: Record<string, string>;
  schema: Record<string, string[]>;
  socket: Socket;
}>) {
  const [entityType, setEntityType] = useState<EntityType>(initialValues);
  const [translationsBuffer, setTranslationsBuffer] = useState<Record<string, string>>({});

  useEffect(() => {
    setEntityType({ ...initialValues, fromClause: formatSql(initialValues.fromClause) });
    setTranslationsBuffer({});
  }, [initialValues]);

  const effectiveTranslations = { ...translations, ...translationsBuffer };

  const codeMirrorExtension = useMemo(
    () =>
      sql({
        dialect: PostgreSQL,
        schema: schema,
        defaultSchema: '${tenant_id}_mod_fqm_manager',
        upperCaseKeywords: true,
      }),
    [schema]
  );

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();

        socket.emit('save-entity-type', { file, entityType });
        // todo: something with translations

        socket.once('saved-entity-type', () => {
          window.alert('Saved!');
          // socket will cause state clearing by pushing list of entity types
        });
      }}
    >
      <fieldset>
        <legend>
          Entity type <code>{entityType.name}</code>
        </legend>

        <pre>
          Translations buffer:
          {JSON.stringify(translationsBuffer, null, 2)}
        </pre>

        <fieldset>
          <legend>Metadata</legend>

          <Grid container spacing={2}>
            <Grid item xs={6}>
              <TextField
                label="ID"
                fullWidth
                value={entityType.id}
                disabled
                inputProps={{ style: { fontFamily: 'monospace' } }}
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton onClick={() => setEntityType({ ...entityType, id: uuid() })}>
                        <Refresh />
                      </IconButton>
                    </InputAdornment>
                  ),
                }}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                label="Name"
                required
                fullWidth
                value={entityType.name}
                onChange={(e) => setEntityType({ ...entityType, name: e.target.value })}
                inputProps={{ style: { fontFamily: 'monospace' } }}
              />
            </Grid>
            <Grid item xs={3}>
              <FormControlLabel
                label="Root"
                control={
                  <Checkbox
                    checked={entityType.root}
                    onChange={(e) => setEntityType({ ...entityType, root: e.target.checked })}
                  />
                }
              />
            </Grid>
            <Grid item xs={3}>
              <FormControlLabel
                label="Private"
                control={
                  <Checkbox
                    checked={entityType.private}
                    onChange={(e) => setEntityType({ ...entityType, private: e.target.checked })}
                  />
                }
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                label="Label"
                helperText="Translation (en), when applicable"
                fullWidth
                onChange={(e) =>
                  setTranslationsBuffer({ ...translationsBuffer, [`entityType.${entityType.name}`]: e.target.value })
                }
                value={effectiveTranslations[`entityType.${entityType.name}`] ?? ''}
              />
            </Grid>
            {/* <Grid item xs={6}>
              <FormControl fullWidth>
                <InputLabel id="custom-field-select-label">Custom field entity type</InputLabel>
                <Select
                  labelId="custom-field-select-label"
                  fullWidth
                  value={entityType.customFieldEntityTypeId ?? ''}
                  onChange={(e) =>
                    e.target.value
                      ? setEntityType({ ...entityType, customFieldEntityTypeId: e.target.value })
                      : setEntityType({ ...entityType, customFieldEntityTypeId: undefined })
                  }
                >
                  <MenuItem value="">
                    <i>None</i>
                  </MenuItem>
                  {entityTypes.map((et) => (
                    <MenuItem key={et.id} value={et.id}>
                      {et.name} ({et.id})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid> */}
            <Grid container item xs={12}>
              <CodeMirror
                style={{ width: 0, flexGrow: 1 }}
                value={entityType.fromClause}
                onChange={(value) => setEntityType({ ...entityType, fromClause: value })}
                extensions={[codeMirrorExtension]}
              />
            </Grid>
          </Grid>
        </fieldset>

        <Alert severity="warning" sx={{ mt: 1, mb: 1 }}>
          Comments, if any, will be removed from the JSON after saving the entity type here. Please double check your
          git diff carefully!
        </Alert>
        <Button type="submit" variant="contained" fullWidth>
          Save
        </Button>
      </fieldset>
    </form>
  );
}
