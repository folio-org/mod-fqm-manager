import { format } from 'sql-formatter';

export function formatSql(sql: string | undefined): string {
  try {
    return format((sql ?? '').replaceAll('${tenant_id}', 'TENANT'), {
      language: 'postgresql',
      tabWidth: 2,
      keywordCase: 'upper',
      denseOperators: true,
      expressionWidth: 60,
    });
  } catch (err) {
    console.warn('Could not format SQL:', { sql, err });
    return sql ?? '';
  }
}
