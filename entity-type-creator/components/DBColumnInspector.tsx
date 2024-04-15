import { Typography } from '@mui/material';
import { pascalCase } from 'change-case';
import { compile } from 'json-schema-to-typescript';
import { format } from 'prettier';
import prettierPluginESTree from 'prettier/plugins/estree.mjs';
import prettierPluginTS from 'prettier/plugins/typescript.mjs';
import { useCallback, useState } from 'react';
import { Socket } from 'socket.io-client';

export default function DBColumnInspector({
  socket,
  db,
  table,
  column,
  dataType,
}: Readonly<{ socket: Socket; db: string; table: string; column: string; dataType: string }>) {
  const [analysis, setAnalysis] = useState<{
    scanned: number;
    total: number;
    finished: boolean;
    result?: unknown;
  } | null>(null);

  const analyze = useCallback(() => {
    setAnalysis({ scanned: 0, total: 0, finished: false });
    socket.emit('analyze-jsonb', { db, table, column });

    socket.on(
      `analyze-jsonb-result-${db}-${table}-${column}`,
      async (result: { scanned: number; total: number; finished: boolean; result?: unknown }) => {
        if (result.finished) {
          socket.off(`analyze-jsonb-result-${db}-${table}-${column}`);

          setAnalysis({
            ...result,
            result: await format(
              await compile(result.result as any, `${pascalCase(db)}${pascalCase(table)}${pascalCase(column)}Schema`, {
                additionalProperties: false,
                bannerComment: '',
                format: false,
                ignoreMinAndMaxItems: true,
              }),
              { parser: 'typescript', plugins: [prettierPluginTS, prettierPluginESTree] }
            ),
          });
        } else {
          setAnalysis(result);
        }
      }
    );
  }, [db, table, column, socket]);

  return (
    <li>
      {column}: {dataType}{' '}
      {dataType === 'jsonb' &&
        (analysis ? (
          analysis.finished ? (
            <>
              <button onClick={analyze}>re-analyze</button>
              <Typography>
                Scanned {analysis.scanned} of {analysis.total} records
                <pre>{analysis.result as string}</pre>
              </Typography>
            </>
          ) : (
            <>
              <button disabled>
                analyzing ({analysis.scanned}/{analysis.total})
              </button>
              <button
                onClick={() => {
                  socket.emit(`abort-analyze-jsonb-${db}-${table}-${column}`);
                  setAnalysis(null);
                }}
              >
                abort
              </button>
            </>
          )
        ) : (
          <button onClick={analyze}>analyze</button>
        ))}
    </li>
  );
}
