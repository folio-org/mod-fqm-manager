export interface FqmConnection {
  host: string;
  port: number;
  tenant: string;
  limit: number;
}

export interface PostgresConnection {
  host: string;
  port: number;
  database: string;
  user: string;
  password: string;
}

export interface DataType {
  dataType: string;
  itemDataType?: DataType;
  properties?: EntityTypeField[];
}

export interface EntityTypeField {
  name: string;
  dataType: DataType;
  isIdColumn?: boolean;
  idColumnName?: string;
  queryable?: boolean;
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
export interface EntityType {
  id: string;
  name: string;
  root?: boolean;
  private?: boolean;
  customFieldEntityTypeId?: string;
  fromClause?: string;
  columns?: EntityTypeField[];
  defaultSort?: { columnName: string; direction: string }[];
  sourceView?: string;
  sourceViewExtractor?: string;
}
