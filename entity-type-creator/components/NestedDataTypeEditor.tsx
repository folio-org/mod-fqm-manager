import { DataType, DataTypeValue, EntityType, EntityTypeSource } from '@/types';
import { LanguageSupport } from '@codemirror/language';
import { Button, FormControl, Grid, InputLabel, MenuItem, Select } from '@mui/material';
import EntityTypeFieldEditor from './EntityTypeFieldEditor';

export default function NestedDataTypeEditor({
  parentName,
  dataType,
  onChange,
  ...rest
}: Readonly<{
  sources: EntityTypeSource[];
  parentName: string;
  dataType: DataType;
  onChange: (newDataType: DataType) => void;
  entityType: EntityType;
  entityTypes: EntityType[];
  codeMirrorExtension: LanguageSupport;
  translations: Record<string, string>;
  setTranslation: (key: string, value: string) => void;
}>) {
  if (dataType.dataType === 'arrayType') {
    return (
      <Grid item xs={12}>
        <fieldset>
          <legend>Nested array data type</legend>

          <Grid container>
            <Grid item xs={12}>
              <FormControl fullWidth>
                <InputLabel id={`${parentName}-arr-data-type`}>Array element data type</InputLabel>
                <Select<DataTypeValue>
                  labelId={`${parentName}-arr-data-type`}
                  fullWidth
                  value={dataType.itemDataType?.dataType}
                  onChange={(e) =>
                    onChange({ ...dataType, itemDataType: { dataType: e.target.value as DataTypeValue } })
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
          </Grid>

          {dataType.itemDataType && (
            <NestedDataTypeEditor
              parentName={parentName}
              dataType={dataType.itemDataType}
              onChange={(newDT) => onChange({ ...dataType, itemDataType: newDT })}
              {...rest}
            />
          )}
        </fieldset>
      </Grid>
    );
  } else if (dataType.dataType === 'objectType') {
    return (
      <Grid item xs={12}>
        <fieldset>
          <legend>Nested object properties</legend>

          {dataType.properties?.map((property, i) => (
            <EntityTypeFieldEditor
              key={i}
              parentName={parentName}
              field={property}
              onChange={(newProperty) =>
                onChange({
                  ...dataType,
                  properties: dataType.properties?.map((c, j) => (j === i ? newProperty : c)),
                })
              }
              first={i === 0}
              last={i === dataType.properties!.length - 1}
              onDuplicate={() =>
                onChange({
                  ...dataType,
                  properties: [...dataType.properties!, { ...property, name: `${property.name}_copy` }],
                })
              }
              onMoveUp={() => {
                const newProperties = dataType.properties!;
                newProperties[i] = dataType.properties![i - 1];
                newProperties[i - 1] = property;
                onChange({
                  ...dataType,
                  properties: newProperties,
                });
              }}
              onMoveDown={() => {
                const newProperties = dataType.properties!;
                newProperties[i] = dataType.properties![i + 1];
                newProperties[i + 1] = property;
                onChange({
                  ...dataType,
                  properties: newProperties,
                });
              }}
              onDelete={() => onChange({ ...dataType, properties: dataType.properties!.filter((_, j) => j !== i) })}
              isNested
              {...rest}
            />
          ))}

          <Button
            onClick={() =>
              onChange({
                ...dataType,
                properties: [...(dataType.properties ?? []), { name: '', dataType: {} as DataType }],
              })
            }
          >
            Add nested property
          </Button>
        </fieldset>
      </Grid>
    );
  } else {
    return null;
  }
}
