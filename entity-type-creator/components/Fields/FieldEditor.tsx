import { ResultRowPretty } from '@/scripts/dump-entity-type-information';
import { useMemo } from 'react';
import { v4 as uuid } from 'uuid';

export default function FieldEditor({
  field,
  onSave,
  onCancel,
}: Readonly<{ field: ResultRowPretty; onSave: (field: ResultRowPretty) => void; onCancel: () => void }>) {
  const formId = useMemo(() => uuid(), []);

  return (
    <>
      <td>
        <input
          form={formId}
          name="Base entity"
          style={{ fontFamily: 'monospace' }}
          defaultValue={field['Base entity']}
        />
      </td>
      <td />

      <td>
        <input
          form={formId}
          name="Simple entity"
          style={{ fontFamily: 'monospace' }}
          defaultValue={field['Simple entity']}
        />
      </td>
      <td />

      <td>
        <input form={formId} name="Name" style={{ fontFamily: 'monospace' }} defaultValue={field['Name']} />
      </td>
      <td />

      <td>
        <input form={formId} name="Label" defaultValue={field['Label']} />
      </td>
      <td />

      <td>
        <input form={formId} name="Table (nested)" defaultValue={field['Table (nested)']} />
      </td>
      <td />

      <td style={{ fontFamily: 'monospace' }}>
        <input form={formId} name="Datatype" defaultValue={field['Datatype']} />
      </td>
      <td />

      <td>
        <input form={formId} name="Values" defaultValue={field['Values']} />
      </td>
      <td />

      <td>
        <input form={formId} name="Operators" defaultValue={field['Operators']} />
      </td>
      <td />

      <td>
        <input form={formId} type="checkbox" name="Queryable" defaultChecked={field['Queryable'] === 'true'} />
      </td>
      <td />

      <td>
        <input
          form={formId}
          type="checkbox"
          name="Showable in results"
          defaultChecked={field['Showable in results'] === 'true'}
        />
      </td>
      <td />

      <td>
        <input form={formId} type="checkbox" name="API Only" defaultChecked={field['API Only'] === 'true'} />
      </td>
      <td />

      <td>
        <input form={formId} type="checkbox" name="Essential" defaultChecked={field['Essential'] === 'true'} />
      </td>
      <td />

      <td>
        <form
          id={formId}
          onSubmit={(e) => {
            e.preventDefault();

            const values = Object.fromEntries(new FormData(e.target as HTMLFormElement).entries());

            values['Queryable'] = values['Queryable'] === 'on' ? 'true' : 'false';
            values['Showable in results'] = values['Showable in results'] === 'on' ? 'true' : 'false';
            values['API Only'] = values['API Only'] === 'on' ? 'true' : 'false';
            values['Essential'] = values['Essential'] === 'on' ? 'true' : 'false';

            onSave(values as unknown as ResultRowPretty);
          }}
        >
          <button type="submit">save</button>
        </form>
        <button onClick={onCancel}>cancel</button>
      </td>
    </>
  );
}
