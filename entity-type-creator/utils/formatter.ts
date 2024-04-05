import { DataType, EntityType, EntityTypeField } from '@/types';

function preferredOrder<T extends object>(obj: T, order: (keyof T)[]) {
  const newObject: Partial<T> = {};
  for (var i = 0; i < order.length; i++) {
    if (obj.hasOwnProperty(order[i]) && obj[order[i]] !== undefined) {
      newObject[order[i]] = obj[order[i]];
    }
  }
  return newObject as T;
}

// desired root key order
const desiredRootKeyOrder = [
  'id',
  'name',
  'root',
  'private',
  'customFieldEntityTypeId',
  'fromClause',
  'columns',
  'defaultSort',
  'sourceView',
  'sourceViewExtractor',
] as (keyof EntityType)[];
const desiredDefaultSortKeyOrder = ['columnName', 'direction'] as (keyof Required<EntityType>['defaultSort'][0])[];
const desiredFieldKeyOrder = [
  'name',
  'dataType',
  'isIdColumn',
  'idColumnName',
  'queryable',
  'visibleByDefault',
  'valueGetter',
  'filterValueGetter',
  'valueFunction',
  'source',
  'valueSourceApi',
  'values',
] as (keyof EntityTypeField)[];
const desiredDataTypeKeyOrder = ['dataType', 'itemDataType', 'properties'] as (keyof DataType)[];

function fixField(field: EntityTypeField) {
  if (Array.isArray(field.dataType?.properties)) {
    field.dataType.properties = field.dataType.properties.map(fixField);
  }
  field.dataType = preferredOrder(field.dataType, desiredDataTypeKeyOrder);
  return preferredOrder<EntityTypeField>(field, desiredFieldKeyOrder);
}

export default function entityTypeFormatter(data: EntityType) {
  data.columns = data.columns?.map(fixField);
  if (data.defaultSort) {
    data.defaultSort = data.defaultSort.map((s) => preferredOrder(s, desiredDefaultSortKeyOrder));
  }
  data = preferredOrder(data, desiredRootKeyOrder);

  return data;
}
