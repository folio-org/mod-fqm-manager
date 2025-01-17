import { fetchAllEntityTypes, fetchEntityType } from '@/socket/fqm';
import { DataType, DataTypeValue, EntityType, EntityTypeField, FqmConnection } from '@/types';
import json5 from 'json5';
import { mkdir, readdir, readFile } from 'node:fs/promises';
import { unparse } from 'papaparse';

if (process.argv.length < 3) {
  console.error('Usage:');
  console.error('  bun scripts/dump-entity-type-information.ts <label>');
  console.error('    Dumps all publicly available entity types to the dump/<label> directory');
  console.error('  bun scripts/dump-entity-type-information.ts <label> <entityTypeId>...');
  console.error('    Dumps the specified entity types to the dump/<label> directory');
  console.error('  bun scripts/dump-entity-type-information.ts <label> local');
  console.error('    Dumps all entity types to the dump, based on the IDs in the current checkout');
  process.exit(1);
}

export interface ResultRow {
  baseEntity: string;
  simpleEntity: string;
  id: string;
  label: string;
  table: string;
  dataType: string;
  values: string;
  operators: string;
  queryable: boolean;
  visibleByDefault: boolean;
  apiOnly: boolean;
  essential: boolean;
}
export interface ResultRowPretty {
  'Entity ID': string;
  'Base entity': string;
  'Simple entity': string;
  Name: string;
  Label: string;
  'Table (nested)': string;
  Datatype: string;
  Values: string;
  Operators: string;
  Queryable: string;
  'Showable in results': string;
  'API Only': string;
  Essential: string;
}

const FQM_CONNECTION: FqmConnection = {
  host: process.env.FQM_HOST!,
  port: parseInt(process.env.FQM_PORT ?? '8080'),
  tenant: process.env.FQM_TENANT!,
  limit: 50,
  user: process.env.FQM_USERNAME,
  password: process.env.FQM_PASSWORD,
};

let entityTypes: { id: string; label?: string }[];
if (process.argv[3] === 'local') {
  const ENTITY_TYPE_FILE_PATH = '../src/main/resources/entity-types/';
  console.log('Looking for entity types in', ENTITY_TYPE_FILE_PATH);
  entityTypes = (
    await Promise.all(
      (await readdir(ENTITY_TYPE_FILE_PATH, { recursive: true }))
        .filter((f) => f.endsWith('.json5'))
        .map(async (f) => {
          try {
            const data = json5.parse((await readFile(ENTITY_TYPE_FILE_PATH + f)).toString());
            return { id: data.id, label: data.name };
          } catch (e) {
            console.error('Error reading entity type file', `${ENTITY_TYPE_FILE_PATH}${f}`, e);
            return null;
          }
        }),
    )
  ).filter((e) => e !== null);
} else if (process.argv.length > 3) {
  entityTypes = process.argv.slice(3).map((id) => ({ id }));
} else {
  const response = JSON.parse(await fetchAllEntityTypes(FQM_CONNECTION));
  if (Array.isArray(response)) {
    entityTypes = response;
  } else {
    entityTypes = response.entityTypes;
  }
}

const dir = `./dump/${process.argv[2]}`;
await mkdir(dir, { recursive: true });

for (const { id, label } of entityTypes) {
  console.log('Dumping information for entity type', label ?? id);

  const entityType = JSON.parse(await fetchEntityType(FQM_CONNECTION, id, true)) as EntityType;
  const filename = `${dir}/${(label ?? entityType.name).replace(/[^a-z0-9]+/gi, '-').toLowerCase()}.csv`;

  const data: ResultRow[] = [];

  for (const column of entityType.columns ?? []) {
    const columnNameParts = column.name.split('.');
    data.push({
      baseEntity: entityType.name,
      simpleEntity: columnNameParts.length > 1 ? columnNameParts[0] : '',
      id: column.name,
      label: column.labelAlias!,
      table: '',
      dataType: getDataType(column.dataType),
      values: await getValues(column),
      operators: await getOperators(column),
      queryable: column.queryable === true,
      visibleByDefault: column.visibleByDefault === true,
      apiOnly: column.hidden === true,
      essential: column.essential === true,
    });

    if (getProperties(column.dataType)) {
      async function addRecursiveProperties(parent: EntityTypeField, property: EntityTypeField) {
        data.push({
          baseEntity: entityType.name,
          simpleEntity: columnNameParts.length > 1 ? columnNameParts[0] : '',
          id: `${parent.name}[*]->${property.name}`,
          label: property.labelAliasFullyQualified!,
          table: `${parent.labelAlias} > ${property.labelAlias}`,
          dataType: getDataType(property.dataType),
          values: await getValues(property),
          operators: await getOperators(property),
          queryable: property.queryable === true,
          visibleByDefault: property.visibleByDefault === true,
          apiOnly: property.hidden === true,
          essential: property.essential === true,
        });

        await Promise.all(
          getProperties(property.dataType).map((inner) =>
            addRecursiveProperties({ ...property, labelAlias: `${parent.labelAlias} > ${property.labelAlias}` }, inner),
          ),
        );
      }

      await Promise.all(getProperties(column.dataType).map((property) => addRecursiveProperties(column, property)));
    }
  }

  await Bun.write(
    Bun.file(filename),
    unparse(
      data.map((r) => ({
        'Entity ID': entityType.id,
        'Base entity': r.baseEntity,
        'Simple entity': r.simpleEntity,
        Name: r.id,
        Label: r.label,
        'Table (nested)': r.table,
        Datatype: r.dataType,
        Values: r.values,
        Operators: r.operators,
        Queryable: r.queryable,
        'Visible by default': r.visibleByDefault,
        'API only': r.apiOnly,
        Essential: r.essential,
      })),
    )
      // remove some utf-8 things to make excel happy
      .replaceAll('—', '-')
      .replaceAll(' ', ' '),
  );
}

function getProperties(dataType: DataType): EntityTypeField[] {
  if (dataType.properties) {
    return dataType.properties;
  } else if (dataType.itemDataType) {
    return getProperties(dataType.itemDataType);
  } else {
    return [];
  }
}

function getDataType(dataType: DataType): string {
  switch (dataType.dataType) {
    case DataTypeValue.arrayType:
    case DataTypeValue.jsonbArrayType:
      return getDataType(dataType.itemDataType!) + '[]';
    case DataTypeValue.booleanType:
      return 'boolean';
    case DataTypeValue.dateType:
      return 'date';
    case DataTypeValue.enumType:
      return 'enum';
    case DataTypeValue.integerType:
      return 'integer';
    case DataTypeValue.numberType:
      return 'number';
    case DataTypeValue.objectType:
      return 'object';
    case DataTypeValue.openUUIDType:
    case DataTypeValue.rangedUUIDType:
    case DataTypeValue.stringUUIDType:
      return 'uuid';
    case DataTypeValue.stringType:
      return 'string';
    default:
      return dataType.dataType;
  }
}
async function getValues(column: EntityTypeField): Promise<string> {
  if (column.valueSourceApi) {
    return `dropdown from API(${column.valueSourceApi.path})`;
  } else if (column.values) {
    if (column.values.length === 2 && column.values[0].value === 'true' && column.values[1].value === 'false') {
      return 'true-false';
    } else {
      return `dropdown hardcoded (${column.values.map((v) => v.label).join(', ')})`;
    }
  } else if (column.source) {
    return `dropdown from entity (${JSON.parse(await fetchEntityType(FQM_CONNECTION, column.source.entityTypeId)).name} -> ${column.source.columnName})`;
  }

  return '';
}

async function getOperators(column: EntityTypeField): Promise<string> {
  if (!column.queryable) {
    return 'not queryable';
  }
  if (getDataType(column.dataType).endsWith('[]')) {
    return 'contains all/any, not contains all/any, empty';
  }
  switch (getDataType(column.dataType)) {
    case 'string':
      if ((await getValues(column)) === '') {
        return '=, !=, contains, starts, empty';
      } else {
        return '=, !=, in, not in, empty';
      }
    case 'uuid':
    case 'enum':
      return '=, !=, in, not in, empty';
    case 'integer':
    case 'number':
    case 'date':
    case 'object':
      return '=, !=, >, >=, <, <≤, empty';
    case 'boolean':
      return '=, !=, empty';
    default:
      return '?';
  }
}
