export interface FqmConnection {
  host: string;
  port: number;
  tenant: string;
  limit: number;
  user?: string;
  password?: string;
}

export interface PostgresConnection {
  host: string;
  port: number;
  database: string;
  user: string;
  password: string;
}

export enum DataTypeValue {
  arrayType = 'arrayType',
  jsonbArrayType = 'jsonbArrayType',
  booleanType = 'booleanType',
  dateType = 'dateType',
  enumType = 'enumType',
  integerType = 'integerType',
  numberType = 'numberType',
  objectType = 'objectType',
  openUUIDType = 'openUUIDType',
  rangedUUIDType = 'rangedUUIDType',
  stringUUIDType = 'stringUUIDType',
  stringType = 'stringType',
}

export interface DataType {
  dataType: DataTypeValue;
  itemDataType?: DataType;
  properties?: EntityTypeField[];
}

export interface EntityTypeField {
  name: string;
  labelAlias?: string;
  labelAliasFullyQualified?: string;
  property?: string;
  dataType: DataType;
  sourceAlias?: string;
  isIdColumn?: boolean;
  idColumnName?: string;
  queryable?: boolean;
  queryOnly?: boolean;
  hidden?: boolean;
  essential?: boolean;
  visibleByDefault?: boolean;
  valueGetter?: string;
  filterValueGetter?: string;
  valueFunction?: string;
  source?: {
    columnName: string;
    entityTypeId: string;
  };
  valueSourceApi?: {
    path: string;
    valueJsonPath: string;
    labelJsonPath: string;
  };
  values?: { value: string; label: string }[];
}

export interface EntityTypeSource {
  type: 'db' | 'entity-type';
  target?: string;
  alias: string;
  id?: string;
  join?: EntityTypeSourceJoin;
  useIdColumns?: boolean;
}

export interface EntityTypeSourceJoin {
  type: string;
  joinTo: string;
  condition: string;
}

export interface EntityType {
  id: string;
  name: string;
  root?: boolean;
  private?: boolean;
  customFieldEntityTypeId?: string;
  fromClause?: string;
  sources?: EntityTypeSource[];
  columns?: EntityTypeField[];
  defaultSort?: { columnName: string; direction: string }[];
  sourceView?: string;
  sourceViewExtractor?: string;
}

export interface Schema {
  columns: Record<string, string[]>;
  routines: Record<string, string[]>;
  typeMapping: Record<string, string>;
  isView: Record<string, boolean>;
}
