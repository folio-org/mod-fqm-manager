import { ResultRowPretty } from '@/scripts/dump-entity-type-information';
import { useEffect, useMemo, useState } from 'react';
import FieldEditor from './FieldEditor';

export function getDiscrepancies(oldField: ResultRowPretty | null, newField: ResultRowPretty | null) {
  if (!oldField || !newField) {
    return {
      baseEntity: {},
      simpleEntity: {},
      name: {},
      label: {},
      table: {},
      datatype: {},
      values: {},
      operators: {},
      queryable: {},
      showable: {},
      apiOnly: {},
      essential: {},
    };
  }

  return {
    baseEntity: oldField['Base entity'] !== newField['Base entity'] ? { backgroundColor: 'wheat' } : {},
    simpleEntity: oldField['Simple entity'] !== newField['Simple entity'] ? { backgroundColor: 'wheat' } : {},
    name: oldField.Name !== newField.Name ? { backgroundColor: 'wheat' } : {},
    label: oldField.Label !== newField.Label ? { backgroundColor: 'wheat' } : {},
    table: oldField['Table (nested)'] !== newField['Table (nested)'] ? { backgroundColor: 'wheat' } : {},
    datatype: oldField.Datatype !== newField.Datatype ? { backgroundColor: 'orange' } : {},
    values: oldField.Values !== newField.Values ? { backgroundColor: 'wheat' } : {},
    operators: oldField.Operators !== newField.Operators ? { backgroundColor: 'wheat' } : {},
    queryable: oldField['Queryable'] !== newField['Queryable'] ? { backgroundColor: 'orange' } : {},
    showable: oldField['Showable in results'] !== newField['Showable in results'] ? { backgroundColor: 'orange' } : {},
    apiOnly: oldField['API Only'] !== newField['API Only'] ? { backgroundColor: 'orange' } : {},
    essential: oldField['Essential'] !== newField['Essential'] ? { backgroundColor: 'orange' } : {},
  };
}

export function getDiscrepanciesBoolean(oldField: ResultRowPretty | null, newField: ResultRowPretty | null) {
  return Object.entries(getDiscrepancies(oldField, newField)).reduce(
    (acc, [k, v]) => {
      acc[k] = Object.keys(v).length > 0;
      return acc;
    },
    {} as Record<string, boolean>,
  );
}

export function getDiscrepanciesList(oldField: ResultRowPretty | null, newField: ResultRowPretty | null) {
  return Object.entries(getDiscrepancies(oldField, newField))
    .filter(([_, v]) => Object.keys(v).length > 0)
    .map(([k, _]) => k);
}

export function EmojiTrueFalse({ value }: { value: string }) {
  if (value === 'true') {
    return '✅';
  } else {
    return '❌';
  }
}

function addBreakOpportunities(str: string) {
  return str.replaceAll(/([\._])/g, '­$1');
}

function FieldContents({
  field,
  discrepancies,
}: Readonly<{
  field: ResultRowPretty;
  discrepancies: ReturnType<typeof getDiscrepancies>;
}>) {
  return (
    <>
      {/* <td style={{ fontFamily: 'monospace' }}>{oldField['Entity ID']}</td><td /> */}
      <td style={{ fontFamily: 'monospace', ...discrepancies.baseEntity }}>
        {addBreakOpportunities(field['Base entity'])}
      </td>
      <td style={discrepancies.baseEntity} />

      <td style={{ fontFamily: 'monospace', ...discrepancies.simpleEntity }}>
        {addBreakOpportunities(field['Simple entity'])}
      </td>
      <td style={discrepancies.simpleEntity} />

      <td style={{ fontFamily: 'monospace', ...discrepancies.name }}>{addBreakOpportunities(field['Name'])}</td>
      <td style={discrepancies.name} />

      <td style={discrepancies.label}>{field['Label']}</td>
      <td style={discrepancies.label} />

      <td style={discrepancies.table}>{field['Table (nested)']}</td>
      <td style={discrepancies.table} />

      <td style={{ fontFamily: 'monospace', ...discrepancies.datatype }}>{field['Datatype']}</td>
      <td style={discrepancies.datatype} />

      <td style={discrepancies.values}>{field['Values']}</td>
      <td style={discrepancies.values} />

      <td style={discrepancies.operators}>{field['Operators']}</td>
      <td style={discrepancies.operators} />

      <td style={discrepancies.queryable}>
        <EmojiTrueFalse value={field['Queryable']} />
      </td>
      <td style={discrepancies.queryable} />

      <td style={discrepancies.showable}>
        <EmojiTrueFalse value={field['Showable in results']} />
      </td>
      <td style={discrepancies.showable} />

      <td style={discrepancies.apiOnly}>
        <EmojiTrueFalse value={field['API Only']} />
      </td>
      <td style={discrepancies.apiOnly} />

      <td style={discrepancies.essential}>
        <EmojiTrueFalse value={field['Essential']} />
      </td>
      <td style={discrepancies.essential} />
    </>
  );
}

export default function FieldRow({
  oldField,
  newField,
  warnings,
  markedForRemoval,
  markForRemoval,
  newVersion,
  setNewVersion,
}: Readonly<{
  oldField: ResultRowPretty | null;
  newField: ResultRowPretty | null;
  warnings: string[];
  markedForRemoval: boolean;
  markForRemoval: () => void;
  newVersion: ResultRowPretty | null;
  setNewVersion: (f: ResultRowPretty | null) => void;
}>) {
  const [isEditing, setIsEditing] = useState<ResultRowPretty | null>(null);

  useEffect(() => {
    // custom added row
    if (oldField === null && newField === null && newVersion === null && isEditing === null) {
      setIsEditing({
        'Entity ID': '',
        'Base entity': '',
        'Simple entity': '',
        Name: '',
        Label: '',
        'Table (nested)': '',
        Datatype: '',
        Values: '',
        Operators: '',
        Queryable: 'false',
        'Showable in results': 'false',
        'API Only': 'false',
        Essential: 'false',
      });
    }
  }, [oldField, newField, isEditing]);

  const discrepancies = useMemo(() => getDiscrepancies(oldField, newField), [oldField, newField]);

  const extraStyles = {
    textDecoration: markedForRemoval || newVersion ? 'line-through' : 'none',
    backgroundColor: markedForRemoval ? 'lightcoral' : undefined,
  };

  return (
    <>
      <tr
        style={{
          borderBottomColor: '#ccc',
          borderTopWidth: '2px',
          ...extraStyles,
        }}
      >
        {oldField ? (
          <FieldContents field={oldField} discrepancies={discrepancies} />
        ) : (
          <td colSpan={24}>
            <em>no original field</em>
          </td>
        )}
        <td
          rowSpan={2 + warnings.length + (markedForRemoval ? 1 : 0) + (isEditing ? -1 : 0) + (newVersion ? 1 : 0)}
          style={{ textAlign: 'center' }}
        >
          {markedForRemoval ? (
            <button onClick={markForRemoval}>unremove</button>
          ) : isEditing ? (
            ''
          ) : newVersion ? (
            <>
              <button
                onClick={() => {
                  setIsEditing(newVersion);
                  setNewVersion(null);
                }}
              >
                edit
              </button>
              <button onClick={() => setNewVersion(null)}>remove</button>
            </>
          ) : (
            <>
              {!!newField && <button onClick={() => setIsEditing(newField)}>edit</button>}
              {!newField && <button onClick={() => setIsEditing(oldField)}>readd</button>}
              {!!newField && <button onClick={markForRemoval}>remove</button>}
            </>
          )}
        </td>
      </tr>
      {!!warnings.length &&
        warnings.map((w, i) => (
          <tr
            key={`${w}${i}`}
            style={{
              borderBottomColor: '#ccc',
              borderTopColor: '#ccc',
              ...extraStyles,
              backgroundColor: 'wheat',
            }}
          >
            <td colSpan={24}>⚠️ {w}</td>
          </tr>
        ))}

      <tr
        style={{
          borderTopColor: '#ccc',
          borderBottomColor: '#ccc',
          ...extraStyles,
        }}
      >
        {isEditing ? (
          <FieldEditor
            field={isEditing}
            onSave={(f) => {
              setNewVersion(f);
              setIsEditing(null);
            }}
            onCancel={() => setIsEditing(null)}
          />
        ) : newField ? (
          <FieldContents field={newField} discrepancies={discrepancies} />
        ) : (
          <td colSpan={24}>
            <em>no new field</em>
          </td>
        )}
      </tr>

      {markedForRemoval && (
        <tr
          style={{
            borderTopColor: '#ccc',
            backgroundColor: 'lightcoral',
          }}
        >
          <td colSpan={24}>
            <strong>marked for removal</strong>
          </td>
        </tr>
      )}
      {newVersion && (
        <tr style={{ borderTopColor: '#ccc', backgroundColor: 'lawngreen' }}>
          <FieldContents field={newVersion} discrepancies={getDiscrepancies(null, null)} />
        </tr>
      )}
    </>
  );
}
