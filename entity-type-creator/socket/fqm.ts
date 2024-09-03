import { EntityType, FqmConnection } from '@/types';

export async function verifyFqmConnection(fqmConnection: FqmConnection) {
  console.log('Attempting to verify FQM connection', fqmConnection);

  try {
    const response = await fetch(`http://${fqmConnection.host}:${fqmConnection.port}/entity-types`, {
      headers: {
        'x-okapi-tenant': fqmConnection.tenant,
      },
    });

    if (response.status !== 200) {
      const result = { connected: false, message: `Failed to connect: got ${response.status} ${response.statusText}` };
      console.log('Non-200 response: ', await response.text());
      return result;
    }

    return { connected: true, message: 'Connected!' };
  } catch (e) {
    console.error(e);
    return { connected: false, message: `Failed to connect: ${(e as any).message}` };
  }
}

export async function fetchEntityType(fqmConnection: FqmConnection, entityTypeId: string, includeHidden = false) {
  const response = await fetch(
    `http://${fqmConnection.host}:${fqmConnection.port}/entity-types/${entityTypeId}?includeHidden=${includeHidden ? 'true' : 'false'}`,
    {
      headers: {
        'x-okapi-tenant': fqmConnection.tenant,
      },
    },
  );

  if (response.status !== 200) {
    throw new Error(`Got ${response.status} ${response.statusText} (${await response.text()})`);
  }

  return await response.text();
}

export async function getColumns(fqmConnection: FqmConnection, entityType: EntityType): Promise<string[]> {
  console.log('Getting columns for', entityType.name, entityType.id);
  const columns = entityType.columns?.map((column) => column.name) ?? [];

  for (const source of entityType.sources ?? []) {
    if (source.type === 'entity-type') {
      const sourceEntityType = await fetchEntityType(fqmConnection, source.id!);
      const sourceEntity = JSON.parse(sourceEntityType) as EntityType;
      columns.push(...(await getColumns(fqmConnection, sourceEntity)).map((c) => `${source.alias}.${c}`));
    }
  }

  return columns;
}

export async function runQuery(fqmConnection: FqmConnection, entityType: EntityType, query: string) {
  const fields = await getColumns(fqmConnection, entityType);
  console.log('Resolved fields for', entityType.name, ':', fields);

  const response = await fetch(
    `http://${fqmConnection.host}:${fqmConnection.port}/query?${new URLSearchParams({
      query,
      entityTypeId: entityType.id,
      fields: fields.join(','),
      limit: `${fqmConnection.limit}`,
    })}`,
    {
      method: 'GET',
      headers: {
        'x-okapi-tenant': fqmConnection.tenant,
      },
    },
  );

  if (response.status !== 200) {
    throw new Error(`Got ${response.status} ${response.statusText}\n${JSON.stringify(await response.json(), null, 2)}`);
  }

  return ((await response.json()).content as Record<string, string>[]) ?? [];
}

export async function runQueryForValues(fqmConnection: FqmConnection, entityType: EntityType, field: string) {
  const response = await fetch(
    `http://${fqmConnection.host}:${fqmConnection.port}/entity-types/${entityType.id}/columns/${field}/values`,
    {
      method: 'GET',
      headers: {
        'x-okapi-tenant': fqmConnection.tenant,
      },
    },
  );

  if (response.status !== 200) {
    throw new Error(`Got ${response.status} ${response.statusText}\n${JSON.stringify(await response.json(), null, 2)}`);
  }

  return ((await response.json()).content as { value: string; label?: string }[]) ?? [];
}

export async function install(fqmConnection: FqmConnection) {
  const response = await fetch(`http://${fqmConnection.host}:${fqmConnection.port}/_/tenant`, {
    method: 'POST',
    headers: {
      'x-okapi-tenant': fqmConnection.tenant,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      module_to: 'foo',
    }),
  });

  const status = `${response.status} ${response.statusText}`;
  console.log('Installing mod-fqm-manager yielded', status);

  return { status, body: await response.text() };
}

export async function uninstall(fqmConnection: FqmConnection) {
  const response = await fetch(`http://${fqmConnection.host}:${fqmConnection.port}/_/tenant`, {
    method: 'POST',
    headers: {
      'x-okapi-tenant': fqmConnection.tenant,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      module_from: 'foo',
    }),
  });

  const status = `${response.status} ${response.statusText}`;
  console.log('Uninstalling mod-fqm-manager yielded', status);

  return { status, body: await response.text() };
}
