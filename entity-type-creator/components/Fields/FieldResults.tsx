import { ResultRowPretty } from '@/scripts/dump-entity-type-information';
import { Button, Container } from '@mui/material';
import { ReactNode, useCallback, useEffect, useState } from 'react';
import { EmojiTrueFalse, getDiscrepanciesBoolean, getDiscrepanciesList } from './FieldRow';
import { unparse } from 'papaparse';

const redProps = {
  style: { color: 'red', '--custom-palette-color': 'var(--ds-icon-accent-red, #FF5630)' },
  'data-text-custom-color': '#ff5630',
};

function Red({ children }: { children: ReactNode }) {
  return (
    <strong>
      <span className="fabric-text-color-mark" {...redProps}>
        {children}
      </span>
    </strong>
  );
}

function tableify(
  rows: (ResultRowPretty & { _label?: string; _notes?: ReactNode; toBold?: Record<string, boolean> })[],
) {
  if (rows.length === 0) {
    return (
      <ol data-indent-level="2">
        <li>none</li>
      </ol>
    );
  }

  const hasLabel = rows.some((r) => r._label);

  return (
    <table>
      <thead>
        <tr>
          {hasLabel && <th />}
          <th>
            <strong>Base entity</strong>
          </th>
          <th>
            <strong>Simple entity</strong>
          </th>
          <th>
            <strong>Name</strong>
          </th>
          <th>
            <strong>Label</strong>
          </th>
          <th>
            <strong>Table (nested)</strong>
          </th>
          <th>
            <strong>Datatype</strong>
          </th>
          <th>
            <strong>Values</strong>
          </th>
          <th>
            <strong>Operators</strong>
          </th>
          <th>
            <strong>Queryable</strong>
          </th>
          <th>
            <strong>Showable in results</strong>
          </th>
          <th>
            <strong>API Only</strong>
          </th>
          <th>
            <strong>Essential</strong>
          </th>
          <th>
            <strong>Notes</strong>
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>
            {hasLabel && (
              <td>
                <strong>{r._label}</strong>
              </td>
            )}
            <td>{r.toBold?.baseEntity ? <Red>{r['Base entity']}</Red> : r['Base entity']}</td>
            <td>{r.toBold?.simpleEntity ? <Red>{r['Simple entity']}</Red> : r['Simple entity']}</td>
            <td>{r.toBold?.name ? <Red>{r.Name}</Red> : r.Name}</td>
            <td>{r.toBold?.label ? <Red>{r.Label}</Red> : r.Label}</td>
            <td>{r.toBold?.table ? <Red>{r['Table (nested)']}</Red> : r['Table (nested)']}</td>
            <td>{r.toBold?.datatype ? <Red>{r.Datatype}</Red> : r.Datatype}</td>
            <td>{r.toBold?.values ? <Red>{r.Values}</Red> : r.Values}</td>
            <td>{r.toBold?.operators ? <Red>{r.Operators}</Red> : r.Operators}</td>
            <td>
              {r.toBold?.queryable ? (
                <Red>
                  <EmojiTrueFalse value={r.Queryable} />
                </Red>
              ) : (
                <EmojiTrueFalse value={r.Queryable} />
              )}
            </td>
            <td>
              {r.toBold?.showable ? (
                <Red {...redProps}>
                  <EmojiTrueFalse value={r['Showable in results']} />
                </Red>
              ) : (
                <EmojiTrueFalse value={r['Showable in results']} />
              )}
            </td>
            <td>
              {r.toBold?.apiOnly ? (
                <Red {...redProps}>
                  <EmojiTrueFalse value={r['API Only']} />
                </Red>
              ) : (
                <EmojiTrueFalse value={r['API Only']} />
              )}
            </td>
            <td>
              {r.toBold?.essential ? (
                <Red {...redProps}>
                  <EmojiTrueFalse value={r.Essential} />
                </Red>
              ) : (
                <EmojiTrueFalse value={r.Essential} />
              )}
            </td>
            <td>{r._notes}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export default function FieldResults({
  fields,
  markedForRemoval,
  newVersions,
}: Readonly<{
  fields: [ResultRowPretty | null, ResultRowPretty | null, string[]][];
  markedForRemoval: number[];
  newVersions: (ResultRowPretty | null)[];
}>) {
  const [results, setResults] = useState<{
    toRemove: ResultRowPretty[];
    toAdd: ResultRowPretty[];
    toChange: { old: ResultRowPretty; new: ResultRowPretty }[];
  } | null>(null);
  const [prettyResults, setPrettyResults] = useState<ReactNode>(null);
  const [link, setLink] = useState<string | null>(null);

  useEffect(() => setResults(null), [fields, markedForRemoval, newVersions]);

  const generateResults = useCallback(() => {
    const toRemove = [] as ResultRowPretty[];
    const toAdd = [] as ResultRowPretty[];
    const toChange = [] as { old: ResultRowPretty; new: ResultRowPretty }[];

    for (const i of markedForRemoval) {
      toRemove.push(fields[i]?.[1]!);
    }
    // newVersions is a sparse array containing only some values
    for (let i = 0; i < newVersions.length; i++) {
      if (!newVersions[i]) {
        continue;
      }
      if (fields[i]?.[1]) {
        toChange.push({ old: fields[i][1]!, new: newVersions[i]! });
      } else {
        toAdd.push(newVersions[i]!);
      }
    }

    setResults({ toRemove, toAdd, toChange });

    setPrettyResults(
      <>
        <ol>
          <li>
            <strong>The following field(s) should be ADDED:</strong>
          </li>
        </ol>
        {tableify(toAdd)}
        <ol start={2}>
          <li>
            <strong>The following field(s) should be CHANGED:</strong>
          </li>
        </ol>
        {toChange.length === 0 ? (
          <ol data-indent-level="2">
            <li>none</li>
          </ol>
        ) : (
          tableify(
            toChange.flatMap(({ old, new: n }, i) => [
              { ...old, _label: 'Current' },
              {
                ...n,
                _label: 'Desired',
                _notes: (
                  <>
                    Changes in:{' '}
                    <ul>
                      {getDiscrepanciesList(old, n).map((d) => (
                        <li key={d}>{d}</li>
                      ))}
                    </ul>
                  </>
                ),
                toBold: getDiscrepanciesBoolean(old, n),
              },
            ]),
          )
        )}
        <ol start={3}>
          <li>
            <strong>The following field(s) should be REMOVED:</strong>
          </li>
        </ol>
        {tableify(toRemove)}
      </>,
    );

    const finalFieldList = [
      ...fields.map((f, i) => {
        if (markedForRemoval.includes(i)) {
          return null;
        }
        return newVersions[i] ?? f[1];
      }),
      ...newVersions.slice(fields.length),
    ].filter((f): f is ResultRowPretty => f !== null && f !== undefined);

    const origFields = [...fields.map((f) => f[1]), ...fields.map((f) => f[0])].filter(
      (f) => f !== null && f !== undefined,
    );
    const finalFieldsWithEntityTypeIds = finalFieldList.map((r) => {
      if (r['Entity ID']) return r;

      return { ...r, 'Entity ID': origFields.find((f) => f['Base entity'] === r['Base entity'])?.['Entity ID'] };
    });

    setLink(URL.createObjectURL(new Blob([unparse(finalFieldsWithEntityTypeIds)], { type: 'text/csv' })));
  }, [fields, markedForRemoval, newVersions]);

  return (
    <Container sx={{ mt: 2 }}>
      <Button variant="contained" onClick={generateResults}>
        Summarize changes
      </Button>
      {results && (
        <fieldset>
          <legend>
            Changes (copy-paste me into Jira, where formatting will appear) (
            <a href={link!} download="result.csv">
              download
            </a>
            )
          </legend>

          {prettyResults}
        </fieldset>
      )}
    </Container>
  );
}
