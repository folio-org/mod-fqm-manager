import { ResultRowPretty } from '@/scripts/dump-entity-type-information';
import { Box } from '@mui/material';
import { SetStateAction } from 'react';
import ColumnResizer from 'react-table-column-resizer';
import FieldRow from './FieldRow';

export default function FieldTable({
  fields,
  markedForRemoval,
  setMarkedForRemoval,
  newVersions,
  setNewVersions,
  extraRows,
  setExtraRows,
}: Readonly<{
  fields: [ResultRowPretty | null, ResultRowPretty | null, string[]][];
  markedForRemoval: number[];
  setMarkedForRemoval: (m: number[]) => void;
  newVersions: (ResultRowPretty | null)[];
  setNewVersions: (n: (ResultRowPretty | null)[]) => void;
  extraRows: number;
  setExtraRows: React.Dispatch<SetStateAction<number>>;
}>) {
  return (
    <Box
      sx={{
        mt: 2,
        '& table, & td, & th, & tr': {
          borderLeft: '1px solid black',
          borderRight: '1px solid black',
          wordWrap: 'break-word',
        },
        '& table, & tr': { borderTop: '1px solid black', borderBottom: '1px solid black' },
        '& table': { borderCollapse: 'collapse' },
        '& td:nth-of-type(2n)': { borderLeft: 'none' },
        '& td:nth-of-type(2n+1)': { borderRight: 'none' },
        '& td:nth-of-type(17), & td:nth-of-type(19), & td:nth-of-type(21), & td:nth-of-type(23), & td:nth-of-type(25)':
          {
            textAlign: 'center',
          },
        '& input': {
          width: '100%',
        },
      }}
    >
      <table className="column_resize_table">
        <thead>
          <tr>
            {/* <th>Entity ID</th> */}
            <th>Base entity</th>
            <ColumnResizer id={0} />
            <th>Simple entity</th>
            <ColumnResizer id={1} />
            <th>Name</th>
            <ColumnResizer id={2} />
            <th>Label</th>
            <ColumnResizer id={3} />
            <th>Table (nested)</th>
            <ColumnResizer id={4} />
            <th>Datatype</th>
            <ColumnResizer id={5} />
            <th>Values</th>
            <ColumnResizer id={6} />
            <th>Operators</th>
            <ColumnResizer id={7} />
            <th>Queryable</th>
            <ColumnResizer id={8} />
            <th>Showable in results</th>
            <ColumnResizer id={9} />
            <th>API Only</th>
            <ColumnResizer id={10} />
            <th>Essential</th>
            <ColumnResizer id={11} />
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {fields.map((f, i) => (
            <FieldRow
              key={i}
              oldField={f[0]}
              newField={f[1]}
              warnings={f[2]}
              markedForRemoval={markedForRemoval.includes(i)}
              markForRemoval={() => {
                if (markedForRemoval.includes(i)) {
                  setMarkedForRemoval(markedForRemoval.filter((m) => m !== i));
                } else {
                  setMarkedForRemoval([...markedForRemoval, i]);
                }
              }}
              newVersion={newVersions[i] ?? null}
              setNewVersion={(v) => {
                const newNewVersions = [...newVersions];
                newNewVersions[i] = v;
                setNewVersions(newNewVersions);
              }}
            />
          ))}
          {Array.from({ length: extraRows }).map((_, i) => (
            <FieldRow
              key={`extra-${i}`}
              oldField={null}
              newField={null}
              warnings={[]}
              markedForRemoval={markedForRemoval.includes(i + fields.length)}
              markForRemoval={() => {
                if (markedForRemoval.includes(i + fields.length)) {
                  setMarkedForRemoval(markedForRemoval.filter((m) => m !== i + fields.length));
                } else {
                  setMarkedForRemoval([...markedForRemoval, i + fields.length]);
                }
              }}
              newVersion={newVersions[i + fields.length] ?? null}
              setNewVersion={(v) => {
                const newNewVersions = [...newVersions];
                newNewVersions[i + fields.length] = v;
                setNewVersions(newNewVersions);
              }}
            />
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={25}>
              <button onClick={() => setExtraRows((e) => e + 1)}>add new</button>
            </td>
          </tr>
        </tfoot>
      </table>
    </Box>
  );
}
