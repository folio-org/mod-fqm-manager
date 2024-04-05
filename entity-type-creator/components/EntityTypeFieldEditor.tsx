import { DataTypeValue, EntityType, EntityTypeField } from '@/types';
import { ArrowDownward, ArrowUpward, Clear } from '@mui/icons-material';
import {
  Button,
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

export default function EntityTypeFieldEditor({
  parentName,
  entityType,
  entityTypes,
  field,
  onChange,
  translations,
  setTranslation,
  first,
  last,
  onMoveDown,
  onMoveUp,
  onDelete,
}: {
  parentName: string;
  entityType: EntityType;
  entityTypes: EntityType[];
  field: EntityTypeField;
  onChange: (newColumn: EntityTypeField) => void;
  translations: Record<string, string>;
  setTranslation: (key: string, value: string) => void;
  first: boolean;
  last: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onDelete: () => void;
}) {
  return (
    <fieldset>
      <legend style={{ margin: '1em 0' }}>
        <code>{field.name}</code>
      </legend>

      <Grid container spacing={2}>
        <Grid item xs={5}>
          <TextField
            label="Name"
            required
            fullWidth
            value={field.name}
            onChange={(e) => onChange({ ...field, name: e.target.value })}
            inputProps={{ style: { fontFamily: 'monospace' } }}
          />
        </Grid>
        <Grid item xs={5} container sx={{ justifyContent: 'space-around' }}>
          <FormControlLabel
            label="Queryable"
            control={
              <Checkbox
                checked={field.queryable}
                onChange={(e) => onChange({ ...field, queryable: e.target.checked })}
              />
            }
          />
          <FormControlLabel
            label="Visible by default"
            control={
              <Checkbox
                checked={field.visibleByDefault}
                onChange={(e) => onChange({ ...field, visibleByDefault: e.target.checked })}
              />
            }
          />
        </Grid>
        <Grid item container xs={2} sx={{ alignItems: 'flex-start', justifyContent: 'space-around' }}>
          <IconButton disabled={first} onClick={onMoveUp}>
            <ArrowUpward />
          </IconButton>
          <IconButton disabled={last} onClick={onMoveDown}>
            <ArrowDownward />
          </IconButton>
          <IconButton onClick={onDelete}>
            <Clear />
          </IconButton>
        </Grid>

        <Grid item xs={5}>
          <TextField
            label="Translation"
            fullWidth
            onChange={(e) => setTranslation(`entityType.${parentName}.${field.name}`, e.target.value)}
            value={translations[`entityType.${parentName}.${field.name}`] ?? ''}
          />
        </Grid>
        <Grid item xs={5}>
          <FormControl fullWidth>
            <InputLabel id={`${parentName}-${field.name}-id-column`}>ID column</InputLabel>
            <Select
              labelId={`${parentName}-${field.name}-id-column`}
              fullWidth
              value={field.idColumnName ?? ''}
              onChange={(e) =>
                e.target.value
                  ? onChange({ ...field, idColumnName: e.target.value })
                  : onChange({ ...field, idColumnName: undefined })
              }
            >
              <MenuItem value="">
                <i>None</i>
              </MenuItem>
              {entityType
                .columns!.filter((f) => f.name !== field.name)
                .map((f) => (
                  <MenuItem key={f.name} value={f.name} sx={{ fontFamily: 'monospace' }}>
                    {f.name}
                  </MenuItem>
                ))}
            </Select>
          </FormControl>
        </Grid>
        <Grid item xs={2} container sx={{ justifyContent: 'space-around' }}>
          <FormControlLabel
            label="Is ID column"
            control={
              <Checkbox
                checked={field.visibleByDefault}
                onChange={(e) => onChange({ ...field, visibleByDefault: e.target.checked })}
              />
            }
          />
        </Grid>

        <Grid item xs={5}>
          <FormControl fullWidth>
            <InputLabel id={`${parentName}-${field.name}-data-type`}>Data type</InputLabel>
            <Select
              labelId={`${parentName}-${field.name}-data-type`}
              fullWidth
              value={field.dataType?.dataType}
              onChange={(e) =>
                onChange({ ...field, dataType: { ...field.dataType, dataType: e.target.value as DataTypeValue } })
              }
            >
              {Object.values(DataTypeValue).map((dt) => (
                <MenuItem key={dt} value={dt} sx={{ fontFamily: 'monospace' }}>
                  {dt}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Grid>

        {useMemo(
          () => (
            <Grid item xs={7}>
              <FormControl fullWidth>
                <InputLabel id={`${parentName}-${field.name}-source-type`}>Source type</InputLabel>
                <Select
                  labelId={`${parentName}-${field.name}-source-type`}
                  fullWidth
                  value={
                    field.valueSourceApi !== undefined
                      ? 'api'
                      : field.source !== undefined
                      ? 'entity'
                      : field.values !== undefined
                      ? 'list'
                      : ''
                  }
                  onChange={(e) => {
                    switch (e.target.value) {
                      case 'entity':
                        onChange({
                          ...field,
                          source: {} as unknown as EntityTypeField['source'],
                          values: undefined,
                          valueSourceApi: undefined,
                        });
                        break;
                      case 'api':
                        onChange({
                          ...field,
                          valueSourceApi: {} as unknown as EntityTypeField['valueSourceApi'],
                          values: undefined,
                          source: { entityTypeId: entityType.id, columnName: field.name },
                        });
                        break;
                      case 'list':
                        onChange({
                          ...field,
                          values: [],
                          valueSourceApi: undefined,
                          source: undefined,
                        });
                        break;
                      default:
                        onChange({ ...field, source: undefined, valueSourceApi: undefined, values: undefined });
                    }
                  }}
                >
                  <MenuItem value="">
                    <i>None</i>
                  </MenuItem>
                  <MenuItem value="entity">Entity type</MenuItem>
                  <MenuItem value="api">API</MenuItem>
                  <MenuItem value="list">Static list</MenuItem>
                </Select>
              </FormControl>
            </Grid>
          ),
          [field.valueSourceApi, field.source, field.values, entityType, onChange, parentName]
        )}

        {useMemo(
          () =>
            field.valueSourceApi !== undefined ? (
              <>
                <Grid item xs={4}>
                  <TextField
                    label="Path"
                    fullWidth
                    inputProps={{ style: { fontFamily: 'monospace' } }}
                    value={field.valueSourceApi.path}
                    onChange={(e) =>
                      onChange({
                        ...field,
                        valueSourceApi: { ...field.valueSourceApi!, path: e.target.value },
                      })
                    }
                  />
                </Grid>
                <Grid item xs={4}>
                  <TextField
                    label="Value JSON path"
                    fullWidth
                    inputProps={{ style: { fontFamily: 'monospace' } }}
                    value={field.valueSourceApi.valueJsonPath}
                    onChange={(e) =>
                      onChange({
                        ...field,
                        valueSourceApi: { ...field.valueSourceApi!, valueJsonPath: e.target.value },
                      })
                    }
                  />
                </Grid>
                <Grid item xs={4}>
                  <TextField
                    label="Label JSON path"
                    fullWidth
                    inputProps={{ style: { fontFamily: 'monospace' } }}
                    value={field.valueSourceApi.labelJsonPath}
                    onChange={(e) =>
                      onChange({
                        ...field,
                        valueSourceApi: { ...field.valueSourceApi!, labelJsonPath: e.target.value },
                      })
                    }
                  />
                </Grid>
              </>
            ) : (
              field.source !== undefined && (
                <>
                  <Grid item xs={6}>
                    <FormControl fullWidth>
                      <InputLabel id={`${parentName}-${field.name}-source-et`}>Source entity type</InputLabel>
                      <Select
                        labelId={`${parentName}-${field.name}-source-et`}
                        fullWidth
                        value={field.source?.entityTypeId}
                        onChange={(e) =>
                          e.target.value
                            ? onChange({
                                ...field,
                                source: { ...(field.source ?? {}), entityTypeId: e.target.value, columnName: '' },
                              })
                            : onChange({ ...field, source: undefined })
                        }
                      >
                        <MenuItem value="">
                          <i>None</i>
                        </MenuItem>
                        {entityTypes.map((et) => (
                          <MenuItem key={et.id} value={et.id} sx={{ fontFamily: 'monospace' }}>
                            {et.name}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Grid>
                  <Grid item xs={6}>
                    {field.source?.entityTypeId && (
                      <FormControl fullWidth>
                        <InputLabel id={`${parentName}-${field.name}-source-col`}>Source column</InputLabel>
                        <Select
                          labelId={`${parentName}-${field.name}-source-col`}
                          fullWidth
                          value={field.source?.columnName}
                          onChange={(e) =>
                            onChange({
                              ...field,
                              source: { ...field.source!, columnName: e.target.value },
                            })
                          }
                        >
                          <MenuItem value="">
                            <i>None</i>
                          </MenuItem>
                          {entityTypes
                            .find((et) => et.id === field.source?.entityTypeId)
                            ?.columns?.map((col) => (
                              <MenuItem key={col.name} value={col.name} sx={{ fontFamily: 'monospace' }}>
                                {col.name}
                              </MenuItem>
                            ))}
                        </Select>
                      </FormControl>
                    )}
                  </Grid>
                </>
              )
            ),
          [field.source, field.valueSourceApi, onChange, parentName, entityTypes]
        )}

        {useMemo(
          () =>
            field.values !== undefined && (
              <Grid item xs={12}>
                <fieldset>
                  <legend>Values</legend>

                  {field.values.map((value, i) => (
                    <Grid container spacing={2} sx={{ mb: 1 }} key={i}>
                      <Grid item xs={5}>
                        <TextField
                          label="Value"
                          fullWidth
                          size="small"
                          inputProps={{ style: { fontFamily: 'monospace' } }}
                          value={value.value}
                          onChange={(e) =>
                            onChange({
                              ...field,
                              values: field.values!.map((v, j) => (j === i ? { ...v, value: e.target.value } : v)),
                            })
                          }
                        />
                      </Grid>
                      <Grid item xs={6}>
                        <TextField
                          label="Label"
                          fullWidth
                          size="small"
                          value={value.label}
                          onChange={(e) =>
                            onChange({
                              ...field,
                              values: field.values!.map((v, j) => (j === i ? { ...v, label: e.target.value } : v)),
                            })
                          }
                        />
                      </Grid>
                      <Grid item xs={1} container sx={{ justifyContent: 'flex-end' }}>
                        <IconButton
                          onClick={() =>
                            onChange({
                              ...field,
                              values: field.values!.filter((_, j) => j !== i),
                            })
                          }
                        >
                          <Clear />
                        </IconButton>
                      </Grid>
                    </Grid>
                  ))}

                  <Button onClick={() => onChange({ ...field, values: [...field.values!, { value: '', label: '' }] })}>
                    add
                  </Button>
                </fieldset>
              </Grid>
            ),
          [field.values]
        )}
      </Grid>
    </fieldset>
  );
}
