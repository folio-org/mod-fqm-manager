import { EntityType, EntityTypeSource } from '@/types';
import { Delete } from '@mui/icons-material';
import {
  Autocomplete,
  Checkbox,
  FormControl,
  FormControlLabel,
  Grid,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  TextField,
} from '@mui/material';
import { useMemo } from 'react';

export default function SourceEditor({
  entityTypes,
  schema,
  source,
  sources,
  isRoot,
  onChange,
  onRemove,
}: {
  entityTypes: EntityType[];
  schema: Record<string, string[]>;
  source: EntityTypeSource;
  sources: EntityTypeSource[];
  isRoot: boolean;
  onChange: (newSource: EntityTypeSource) => void;
  onRemove: () => void;
}) {
  const dbSources = useMemo(
    () =>
      Object.keys(schema)
        .filter((k) => k.startsWith('TENANT_mod_fqm_manager.'))
        .map((k) => k.substring(23))
        .filter((k) => !k.startsWith('query_results_'))
        .toSorted(),
    [schema],
  );

  return (
    <fieldset style={isRoot ? {} : { marginTop: '0.5em' }}>
      <legend style={{ fontFamily: 'monospace' }}>{source.alias}</legend>

      <Grid container spacing={2}>
        <Grid item xs={2}>
          <FormControl fullWidth>
            <InputLabel id={`${source.alias}-source-type`}>Type</InputLabel>
            <Select<'db' | 'entity-type'>
              labelId={`${source.alias}-source-type`}
              fullWidth
              value={source.type}
              onChange={(e) => onChange({ alias: source.alias, type: e.target.value as 'db' | 'entity-type' })}
            >
              <MenuItem value="db" sx={{ fontFamily: 'monospace' }}>
                db
              </MenuItem>
              <MenuItem value="entity-type" sx={{ fontFamily: 'monospace' }}>
                entity-type
              </MenuItem>
            </Select>
          </FormControl>
        </Grid>
        <Grid item xs={4}>
          <TextField
            label="Alias"
            required
            fullWidth
            value={source.alias}
            onChange={(e) => onChange({ ...source, alias: e.target.value })}
            inputProps={{ style: { fontFamily: 'monospace' } }}
          />
        </Grid>
        <Grid item xs={5}>
          {source.type === 'entity-type' ? (
            <FormControl fullWidth>
              <InputLabel id={`${source.alias}-entity-type`}>Entity type</InputLabel>
              <Select
                labelId={`${source.alias}-entity-type`}
                fullWidth
                value={source.id ?? ''}
                onChange={(e) => onChange({ ...source, id: e.target.value })}
              >
                {entityTypes
                  .toSorted((a, b) => a.name.localeCompare(b.name))
                  .map((et) => (
                    <MenuItem key={et.id} value={et.id}>
                      {et.name}
                    </MenuItem>
                  ))}
              </Select>
            </FormControl>
          ) : (
            <Autocomplete
              freeSolo
              options={dbSources}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Source view"
                  required
                  value={source.target}
                  onChange={(e) => onChange({ ...source, target: e.target.value })}
                />
              )}
            />
          )}
        </Grid>
        <Grid item xs={1} container sx={{ alignItems: 'center', justifyContent: 'center' }}>
          <IconButton onClick={onRemove}>
            <Delete />
          </IconButton>
        </Grid>
        <Grid item xs={12}>
          <FormControlLabel
            label="Use ID columns"
            control={
              <Checkbox
                indeterminate={source.useIdColumns === undefined}
                checked={source.useIdColumns}
                onChange={(e) => onChange({ ...source, useIdColumns: e.target.checked })}
              />
            }
          />
        </Grid>

        {!isRoot && (
          <>
            <Grid item xs={3}>
              <TextField
                label="Join type"
                fullWidth
                value={source.join?.type ?? ''}
                onChange={(e) =>
                  onChange({
                    ...source,
                    join: { ...(source.join ?? {}), type: e.target.value } as EntityTypeSource['join'],
                  })
                }
                inputProps={{ style: { fontFamily: 'monospace' } }}
              />
            </Grid>
            <Grid item xs={3}>
              <Autocomplete
                freeSolo
                options={sources.map((s) => s.alias).filter((a) => a != source.alias)}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Join to"
                    required
                    value={source.join?.joinTo ?? ''}
                    onChange={(e) =>
                      onChange({
                        ...source,
                        join: { ...(source.join ?? {}), joinTo: e.target.value } as EntityTypeSource['join'],
                      })
                    }
                  />
                )}
              />
            </Grid>
            <Grid item xs={6}>
              <TextField
                label="Join condition"
                fullWidth
                helperText="Use :this and :that as placeholders for the current and joined table"
                value={source.join?.condition ?? ''}
                onChange={(e) =>
                  onChange({
                    ...source,
                    join: { ...(source.join ?? {}), condition: e.target.value } as EntityTypeSource['join'],
                  })
                }
                inputProps={{ style: { fontFamily: 'monospace' } }}
              />
            </Grid>
          </>
        )}
      </Grid>
    </fieldset>
  );
}
