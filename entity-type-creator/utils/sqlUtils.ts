import { format } from 'sql-formatter';

export function formatSql(sql: string | undefined): string {
  return format(sql ?? '', {
    language: 'postgresql',
    tabWidth: 2,
    keywordCase: 'upper',
    denseOperators: true,
    expressionWidth: 60,
  });
}
