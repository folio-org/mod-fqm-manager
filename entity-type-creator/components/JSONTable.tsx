import { useMemo } from 'react';

export default function JSONTable({ data }: Readonly<{ data: Record<string, unknown>[] }>) {
  const keys = useMemo(() => {
    const keys = new Set<string>();
    for (const row of data) {
      if (row === null || row === undefined) continue;
      for (const key of Object.keys(row)) {
        keys.add(key);
      }
    }
    return Array.from(keys);
  }, [data]);
  const jsonKeys = useMemo(() => {
    const jsonKeys = {} as Record<string, boolean>;
    for (const key of keys) {
      jsonKeys[key] = true;
    }
    for (const key of Object.keys(jsonKeys)) {
      for (const row of data) {
        if (row[key] === undefined || row[key] === null) continue;

        if (typeof row[key] !== 'string' || !(row[key] as string).startsWith('[')) {
          jsonKeys[key] = false;
          break;
        } else {
          try {
            JSON.parse(row[key] as string);
          } catch (e) {
            jsonKeys[key] = false;
            break;
          }
        }
      }
    }

    return jsonKeys;
  }, [keys, data]);

  if (data.length === 0) {
    return <i style={{ color: '#ccc' }}>none</i>;
  }

  return (
    <table style={{ fontFamily: 'monospace', textAlign: 'left', border: '1px solid', borderCollapse: 'collapse' }}>
      <thead>
        <tr>
          {keys.map((key) => (
            <th key={key} style={{ border: '1px solid' }}>
              {key}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {data.map((row, i) => (
          <tr key={i}>
            {keys.map((key) => (
              <td
                key={key}
                style={{
                  border: '1px solid',
                  verticalAlign: 'top',
                  maxHeight: '4em',
                  overflowY: 'auto',
                  whiteSpace: 'pre-wrap',
                }}
              >
                {(() => {
                  if (row[key] === null || row[key] === undefined) {
                    return <span style={{ color: 'gray', fontStyle: 'italic' }}>{JSON.stringify(row[key])}</span>;
                  } else if (Array.isArray(row[key])) {
                    return (row[key] as string[]).join('\n');
                  } else if (jsonKeys[key]) {
                    return <JSONTable data={JSON.parse(row[key] as string)} />;
                  } else if (typeof row[key] === 'object' || row[key] === true || row[key] === false) {
                    return JSON.stringify(row[key], null, 2);
                  } else {
                    return row[key] as string | number;
                  }
                })()}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
