import { EntityType, Schema } from '@/types';
import { Box } from '@mui/system';
import { useMemo } from 'react';
import { Socket } from 'socket.io-client';
import DBColumnInspector from './DBColumnInspector';

export default function DBInspector({
  socket,
  schema,
  entityType,
}: Readonly<{
  socket: Socket;
  schema: Schema;
  entityType: EntityType | null;
}>) {
  const hierarchicalSchema = useMemo(() => {
    const result: Record<
      string,
      {
        routines: string[];
        tables: Record<string, Record<string, string>>;
        views: Record<string, Record<string, string>>;
      }
    > = {};
    for (const db of Object.keys(schema.routines)) {
      result[db] = { routines: schema.routines[db], tables: {}, views: {} };
    }
    for (const dbTableColumn of Object.keys(schema.typeMapping)) {
      const [db, table, column] = dbTableColumn.split('.');
      result[db] = result[db] ?? { routines: [], tables: {}, views: {} };

      if (schema.isView[`${db}.${table}`]) {
        result[db].views[table] = result[db].views[table] ?? {};
        result[db].views[table][column] = schema.typeMapping[dbTableColumn];
      } else {
        result[db].tables[table] = result[db].tables[table] ?? {};
        result[db].tables[table][column] = schema.typeMapping[dbTableColumn];
      }
    }
    return result;
  }, [schema]);

  return (
    <Box sx={{ fontFamily: 'monospace' }}>
      {Object.keys(hierarchicalSchema)
        .toSorted((a, b) => a.localeCompare(b))
        .map((db) => (
          <details key={db}>
            <summary>
              {db.replace('TENANT_', '')} (
              {Object.keys(hierarchicalSchema[db].tables).length + Object.keys(hierarchicalSchema[db].views).length})
            </summary>

            <details style={{ marginLeft: '1em' }}>
              <summary>🧮 Routines ({hierarchicalSchema[db].routines.length})</summary>

              <ul>
                {hierarchicalSchema[db].routines
                  .toSorted((a, b) => a.localeCompare(b))
                  .map((routine) => (
                    <li key={routine}>{routine}</li>
                  ))}
              </ul>
            </details>

            {Object.keys(hierarchicalSchema[db].tables)
              .toSorted((a, b) => a.localeCompare(b))
              .map((table) => {
                const source = entityType?.sources?.find((s) => s.target === table)?.alias;
                return (
                  <details key={table} style={{ marginLeft: '1em' }}>
                    <summary>
                      💾 {table} ({Object.keys(hierarchicalSchema[db].tables[table]).length})
                      {!!source && ` (🔗 source alias ${source})`}
                    </summary>

                    <ul>
                      {Object.keys(hierarchicalSchema[db].tables[table])
                        .toSorted((a, b) => a.localeCompare(b))
                        .map((column) => (
                          <DBColumnInspector
                            key={column}
                            socket={socket}
                            db={db}
                            table={table}
                            column={column}
                            dataType={hierarchicalSchema[db].tables[table][column]}
                            entityType={entityType}
                          />
                        ))}
                    </ul>
                  </details>
                );
              })}
            {Object.keys(hierarchicalSchema[db].views)
              .toSorted((a, b) => a.localeCompare(b))
              .map((view) => {
                const source = entityType?.sources?.find((s) => s.target === view)?.alias;
                return (
                  <details key={view} style={{ marginLeft: '1em' }}>
                    <summary>
                      ✨ {view} ({Object.keys(hierarchicalSchema[db].views[view]).length})
                      {!!source && ` (🔗 source alias ${source})`}
                    </summary>

                    <ul>
                      {Object.keys(hierarchicalSchema[db].views[view])
                        .toSorted((a, b) => a.localeCompare(b))
                        .map((column) => (
                          <DBColumnInspector
                            key={column}
                            socket={socket}
                            db={db}
                            table={view}
                            column={column}
                            dataType={hierarchicalSchema[db].views[view][column]}
                            entityType={entityType}
                          />
                        ))}
                    </ul>
                  </details>
                );
              })}
          </details>
        ))}
    </Box>
  );
}
