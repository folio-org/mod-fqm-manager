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
