import FieldResults from '@/components/Fields/FieldResults';
import FieldTable from '@/components/Fields/FieldTable';
import Uploader from '@/components/Fields/Uploader';
import { ResultRowPretty } from '@/scripts/dump-entity-type-information';
import { Mapping } from '@/scripts/get-column-mapping';
import { Container } from '@mui/material';
import { SetStateAction, useEffect, useMemo, useState } from 'react';

export default function FieldComparison() {
  const [oldFields, setOldFields] = useState<ResultRowPretty[]>([]);
  const [newFields, setNewFields] = useState<ResultRowPretty[]>([]);
  const [mapping, setMapping] = useState<Mapping[]>([]);

  const fields = useMemo(() => {
    const result: [ResultRowPretty | null, ResultRowPretty | null, string[]][] = [];

    const newFieldsCopy = [...newFields];
    for (const oldField of oldFields) {
      let map = mapping.find((m) => m.src.entityType === oldField['Entity ID'] && m.src.column === oldField.Name);
      let newField =
        map && newFieldsCopy.find((f) => f['Entity ID'] === map.dest.entityType && f.Name === map.dest.column);

      if (!newField) {
        newField = newFieldsCopy.find((f) => f['Entity ID'] === oldField['Entity ID'] && f.Name === oldField.Name);
      }

      if (newField) {
        newFieldsCopy.splice(newFieldsCopy.indexOf(newField), 1);
      }

      result.push([oldField, newField ?? null, map?.warnings ?? []]);
    }

    for (const newField of newFieldsCopy) {
      result.push([null, newField, []]);
    }

    return result.toSorted((a, b) => {
      // ick
      if (a[0] === null && b[0] !== null) {
        return 1;
      } else if (a[0] !== null && b[0] === null) {
        return -1;
      } else if (a[1]?.['Entity ID'] !== b[1]?.['Entity ID']) {
        return (a[1]?.['Entity ID'] ?? '').localeCompare(b[1]?.['Entity ID'] ?? '');
      } else if (a[0]?.['Entity ID'] !== b[0]?.['Entity ID']) {
        return (a[0]?.['Entity ID'] ?? '').localeCompare(b[0]?.['Entity ID'] ?? '');
      } else if (a[1]?.['Name'] && b[1]?.['Name']) {
        return a[1]['Name'].localeCompare(b[1]['Name']);
      } else if (a[0]?.['Name'] && b[0]?.['Name']) {
        return a[0]['Name'].localeCompare(b[0]['Name']);
      } else {
        return 0;
      }
    });
  }, [oldFields, newFields, mapping]);

  const [markedForRemoval, setMarkedForRemoval] = useState<number[]>([]);
  const [newVersions, setNewVersions] = useState<(ResultRowPretty | null)[]>([]);
  const [extraRows, setExtraRows] = useState<number>(0);

  useEffect(() => {
    setMarkedForRemoval([]);
    setNewVersions([]);
    setExtraRows(0);
  }, [fields]);

  return (
    <>
      <Container>
        <Uploader setOldFields={setOldFields} setNewFields={setNewFields} setMapping={setMapping} />
      </Container>

      <FieldTable
        fields={fields}
        markedForRemoval={markedForRemoval}
        setMarkedForRemoval={setMarkedForRemoval}
        newVersions={newVersions}
        setNewVersions={setNewVersions}
        extraRows={extraRows}
        setExtraRows={setExtraRows}
      />

      <FieldResults fields={fields} markedForRemoval={markedForRemoval} newVersions={newVersions} />
    </>
  );
}
