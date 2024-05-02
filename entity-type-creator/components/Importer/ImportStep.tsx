import { DataType, DataTypeValue, EntityType, EntityTypeField } from '@/types';
import { json } from '@codemirror/lang-json';
import { Alert, Autocomplete, Button, DialogActions, DialogContent, Grid, TextField, Typography } from '@mui/material';
import CodeMirror, { EditorView } from '@uiw/react-codemirror';
import { sentenceCase, snakeCase } from 'change-case';
import { Schema } from 'genson-js/dist';
import { Dispatch, SetStateAction, useEffect, useMemo, useState } from 'react';
import { END_PAGE, State } from './JSONSchemaImporter';
import EntityTypeFieldEditor from '../EntityTypeFieldEditor';
import { PostgreSQL, sql } from '@codemirror/lang-sql';

export default function ImportStep({
  entityType,
  state,
  setState,
  onClose,
}: Readonly<{
  entityType: EntityType;
  state: State;
  setState: Dispatch<SetStateAction<State>>;
  onClose: () => void;
}>) {
  const prop = Object.keys(state.schema!.properties)[state.page];
  const propSchema = state.schema!.properties[prop];

  const [provisionalIssues, setProvisionalIssues] = useState<string[]>([]);
  const [provisionalColumn, setProvisionalColumn] = useState<EntityTypeField | null>(null);
  const [provisionalTranslations, setProvisionalTranslations] = useState<Record<string, string>>({});

  useEffect(() => {
    if (prop === undefined) {
      setState((s) => ({ ...s, page: END_PAGE }));
      return;
    }
    const { issues, column } = inferColumnFromSchema(entityType, state.source, prop, propSchema);
    const translations = inferTranslationsFromColumn(column, entityType.name);
    setProvisionalIssues(issues);
    setProvisionalColumn(column ?? null);
    setProvisionalTranslations(translations);
  }, [prop]);

  return (
    <>
      <DialogContent>
        <fieldset>
          <legend>Raw schema</legend>
          <pre style={{ whiteSpace: 'pre-wrap' }}>{JSON.stringify({ _name: prop, ...propSchema }, null, 2)}</pre>
        </fieldset>
        {provisionalIssues.length > 0 && (
          <Alert severity="warning">
            <Typography>
              The following issues were encountered while trying to infer the column from the schema:
              <ul>
                {provisionalIssues.map((issue) => (
                  <li key={issue}>{issue}</li>
                ))}
              </ul>
              If the column is not displayed below, then these error(s) were catastrophic.
            </Typography>
          </Alert>
        )}
        The below is <strong>for preview only</strong>. If you wish to edit it, wait until after import.
        <div style={{ pointerEvents: 'none' }}>
          {provisionalColumn && (
            <EntityTypeFieldEditor
              parentName={entityType.name}
              entityType={entityType}
              entityTypes={[]}
              sources={[]}
              codeMirrorExtension={sql({
                dialect: PostgreSQL,
                upperCaseKeywords: true,
              })}
              field={provisionalColumn}
              onChange={() => ({})}
              translations={provisionalTranslations}
              setTranslation={() => ({})}
              first={true}
              last={true}
              onDuplicate={() => ({})}
              onMoveUp={() => ({})}
              onMoveDown={() => ({})}
              onDelete={() => ({})}
            />
          )}
        </div>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} color="error">
          Cancel
        </Button>
        <Button onClick={() => setState((s) => ({ ...s, page: s.page + 1 }))}>Skip</Button>
        {provisionalColumn && (
          <Button
            onClick={() =>
              setState((s) => ({
                ...s,
                page: s.page + 1,
                columns: [...s.columns, provisionalColumn],
                warnings: [...s.warnings, ...provisionalIssues.map((e) => `${provisionalColumn.name}: ${e}`)],
                translations: { ...s.translations, ...provisionalTranslations },
              }))
            }
          >
            Next
          </Button>
        )}
      </DialogActions>
    </>
  );
}

function getSimpleTypeOf(schema: Schema) {
  console.log('Getting type of', schema);
  if ('$ref' in schema) {
    console.log('We are a reference!');
    const ref = schema.$ref as string;

    if (RegExp(/\buuid\b/i).exec(ref)) {
      return 'uuid' as const;
    } else {
      return 'unknown-ref' as const;
    }
  } else {
    switch (Array.isArray(schema.type) ? schema.type.join(',') : schema.type) {
      case 'string,null':
      case 'string':
        if ('format' in schema && schema.format === 'date-time') {
          return 'date' as const;
        } else if ('format' in schema && schema.format === 'date') {
          return 'date' as const;
        } else if ('format' in schema && schema.format === 'uuid') {
          return 'uuid' as const;
        } else {
          return 'string' as const;
        }
      case 'boolean':
        return 'boolean' as const;
      case 'number':
        return 'number' as const;
      case 'integer':
        return 'integer' as const;
      case 'array':
        return 'array' as const;
      case 'object':
        return 'object' as const;
      default:
        console.warn('Unknown type', schema);
        return 'unknown' as const;
    }
  }
}

function getDataType(schema: Schema, path: string): [DataType, string[]] {
  const resolvedType = getSimpleTypeOf(schema);
  const issues: string[] = [];
  if (resolvedType === 'unknown-ref') {
    issues.push(`Unknown reference type: ${(schema as any).$ref}`);
  } else if (resolvedType === 'unknown') {
    issues.push(`Unknown type: ${schema.type}`);
  }

  switch (resolvedType) {
    case 'string':
    case 'unknown':
    case 'unknown-ref':
      return [{ dataType: DataTypeValue.stringType }, issues];
    case 'date':
      return [{ dataType: DataTypeValue.dateType }, issues];
    case 'uuid':
      return [{ dataType: DataTypeValue.rangedUUIDType }, issues];
    case 'boolean':
      return [{ dataType: DataTypeValue.booleanType }, issues];
    case 'number':
      return [{ dataType: DataTypeValue.numberType }, issues];
    case 'integer':
      return [{ dataType: DataTypeValue.integerType }, issues];
    case 'array': {
      if (!schema.items) {
        issues.push('Array type with unknown item type; defaulting to string');
        return [{ dataType: DataTypeValue.arrayType, itemDataType: { dataType: DataTypeValue.stringType } }, issues];
      }
      const [innerDataType, innerErrors] = getDataType(schema.items, path);
      issues.push(...innerErrors.map((e) => `in array: ${e}`));
      return [{ dataType: DataTypeValue.arrayType, itemDataType: innerDataType }, issues];
    }
    case 'object': {
      const properties: EntityTypeField[] = Object.entries(schema.properties ?? {}).map(([prop, propSchema]) => {
        const [innerDataType, innerIssues] = getDataType(propSchema, `${path}->'${prop}'`);
        issues.push(...innerIssues.map((e) => `in object property ${prop}: ${e}`));
        return {
          name: snakeCase(prop),
          property: prop,
          dataType: innerDataType,
          queryable: false,
          valueGetter: `( SELECT array_agg(elems.value->>'${prop}') FROM jsonb_array_elements(:sourceAlias.jsonb${path}) AS elems)`,
          filterValueGetter: `( SELECT array_agg(lower(elems.value->>'${prop}')) FROM jsonb_array_elements(:sourceAlias.jsonb${path}) AS elems)`,
          valueFunction: 'lower(:value)',
          values: getValues(innerDataType, (propSchema as { enum?: string[] }).enum),
        };
      });
      return [{ dataType: DataTypeValue.objectType, properties }, issues];
    }
    default:
      console.error('Unknown type', resolvedType, schema);
      issues.push(`Unknown type: ${resolvedType}`);
      return [{ dataType: DataTypeValue.stringType }, issues];
  }
}

function getValues(dataType: DataType, enumValues?: string[]): { value: string; label: string }[] | undefined {
  if (enumValues) {
    return enumValues.map((v) => ({ value: v, label: sentenceCase(v) }));
  } else if (dataType.dataType === DataTypeValue.booleanType) {
    return [
      { value: 'true', label: 'True' },
      { value: 'false', label: 'False' },
    ];
  } else {
    return undefined;
  }
}

function inferColumnFromSchema(
  entityType: EntityType,
  source: string,
  prop: string,
  propSchema: Schema,
): { issues: string[]; column?: EntityTypeField } {
  console.log('Examining ', prop, propSchema);

  if ('folio:isVirtual' in propSchema && propSchema['folio:isVirtual']) {
    return {
      issues: ['It looks like this is a virtual property (folio:isVirtual=true); ignoring?'],
    };
  }

  const issues: string[] = [];

  const name = snakeCase(prop);

  if (entityType.columns?.find((f) => f.name === name)) {
    return {
      issues: [
        `This appears to be a duplicate of an already existing column name ${name}. If you want to import this column, rename the existing column first.`,
      ],
    };
  }

  const [dataType, dtIssues] = getDataType(propSchema, `->'${prop}'`);
  issues.push(...dtIssues);

  const fullPath = `->'${prop}'`.replace(/->([^>]+)$/, '->>$1');

  return {
    issues,
    column: {
      name,
      dataType,
      sourceAlias: source,
      queryable: ![DataTypeValue.arrayType, DataTypeValue.objectType].includes(dataType.dataType),
      visibleByDefault: false,
      isIdColumn: name === 'id',
      valueGetter:
        // if primitive array
        dataType.dataType === DataTypeValue.arrayType && dataType.itemDataType?.dataType !== DataTypeValue.objectType
          ? `( SELECT array_agg(elems.value::text) FROM jsonb_array_elements(:sourceAlias.jsonb->'${prop}') AS elems)`
          : `:sourceAlias.jsonb${fullPath}`,
      filterValueGetter:
        dataType.dataType === DataTypeValue.arrayType && dataType.itemDataType?.dataType !== DataTypeValue.objectType
          ? `( SELECT array_agg(lower(elems.value::text)) FROM jsonb_array_elements(:sourceAlias.jsonb->'${prop}') AS elems)`
          : undefined,
      values: getValues(dataType, (propSchema as { enum?: string[] }).enum),
    },
  };
}

export function inferTranslationsFromColumn(
  column: EntityTypeField | undefined,
  parentName: string,
): Record<string, string> {
  if (!column) {
    return {};
  }

  const translations: Record<string, string> = {};

  const stack = [{ key: `entityType.${parentName}.${column.name}`, name: column.name, dt: column.dataType }];
  while (stack.length) {
    const { key, name, dt } = stack.pop()!;

    translations[key] = sentenceCase(name);
    if (dt.dataType === DataTypeValue.rangedUUIDType) {
      translations[key] = translations[key].replace(/\bid\b/i, 'UUID');
    }

    if (dt.dataType === DataTypeValue.objectType) {
      dt.properties?.forEach((prop) => {
        stack.push({ key: `${key}.${prop.name}`, name: prop.name, dt: prop.dataType });
        stack.push({ key: `${key}.${prop.name}._qualified`, name: `${name} ${prop.name}`, dt: prop.dataType });
      });
    } else if (dt.dataType === DataTypeValue.arrayType) {
      stack.push({ key, name, dt: dt.itemDataType! });
    }
  }

  return translations;
}
