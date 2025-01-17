import { EntityType, FqmConnection } from '@/types';

let token: { 'x-okapi-token'?: string } = {};

export async function authenticate(fqmConnection: FqmConnection) {
  if (!fqmConnection.user) {
    console.error('Cannot authenticate to FQM, no user provided');
  }

  token = {
    'x-okapi-token': (
      await fetch(
        `http${fqmConnection.port === 443 ? 's' : ''}://${fqmConnection.host}:${fqmConnection.port}/authn/login`,
        {
          method: 'POST',
          headers: {
            'x-okapi-tenant': fqmConnection.tenant,
            'content-type': 'application/json',
          },
          body: JSON.stringify({
            username: fqmConnection.user,
            password: fqmConnection.password,
            tenant: fqmConnection.tenant,
          }),
        },
      )
    ).headers.get('x-okapi-token')!,
  };

  if (token['x-okapi-token'] === null) {
    console.error('Traditional token login failed, trying with expiry');
    const response = await fetch(
      `http${fqmConnection.port === 443 ? 's' : ''}://${fqmConnection.host}:${fqmConnection.port}/authn/login-with-expiry`,
      {
        method: 'POST',
        headers: {
          'x-okapi-tenant': fqmConnection.tenant,
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          username: fqmConnection.user,
          password: fqmConnection.password,
          tenant: fqmConnection.tenant,
        }),
      },
    );
    token = {
      'x-okapi-token': response.headers
        .get('set-cookie')
        ?.split(';')
        .find((c) => c.startsWith('folioAccessToken='))!
        .replace('folioAccessToken=', ''),
    };
  }

  console.log('Successfully grabbed authentication token', token);
}

export async function verifyFqmConnection(fqmConnection: FqmConnection) {
  console.log('Attempting to verify FQM connection', fqmConnection);

  try {
    const response = await fetch(
      `http${fqmConnection.port === 443 ? 's' : ''}://${fqmConnection.host}:${fqmConnection.port}/entity-types`,
      {
        headers: {
          'x-okapi-tenant': fqmConnection.tenant,
          ...token,
        },
      },
    );

    if (response.status !== 200) {
      const text = await response.text();
      if (text.includes('Token missing')) {
        await authenticate(fqmConnection);
        return verifyFqmConnection(fqmConnection);
      }
      const result = { connected: false, message: `Failed to connect: got ${response.status} ${response.statusText}` };
      console.log('Non-200 response: ', text);
      return result;
    }

    return { connected: true, message: 'Connected!' };
  } catch (e) {
    console.error(e);
    return { connected: false, message: `Failed to connect: ${(e as any).message}` };
  }
}

export async function fetchAllEntityTypes(fqmConnection: FqmConnection, includeHidden = false) {
  const response = await fetch(
    `http${fqmConnection.port === 443 ? 's' : ''}://${fqmConnection.host}:${fqmConnection.port}/entity-types?includeHidden=${includeHidden ? 'true' : 'false'}`,
    {
      headers: {
        'x-okapi-tenant': fqmConnection.tenant,
        ...token,
      },
    },
  );

  if (response.status !== 200) {
    const text = await response.text();
    if (text.includes('Token missing')) {
      await authenticate(fqmConnection);
      return fetchAllEntityTypes(fqmConnection, includeHidden);
    }
    throw new Error(`Got ${response.status} ${response.statusText} (${text})`);
  }

  return await response.text();
}

export async function fetchEntityType(fqmConnection: FqmConnection, entityTypeId: string, includeHidden = false) {
  const response = await fetch(
    `http${fqmConnection.port === 443 ? 's' : ''}://${fqmConnection.host}:${fqmConnection.port}/entity-types/${entityTypeId}?includeHidden=${includeHidden ? 'true' : 'false'}`,
    {
      headers: {
        'x-okapi-tenant': fqmConnection.tenant,
        ...token,
      },
    },
  );

  if (response.status !== 200) {
    const text = await response.text();
    if (text.includes('Token missing')) {
      await authenticate(fqmConnection);
      return fetchEntityType(fqmConnection, entityTypeId, includeHidden);
    }
    throw new Error(`Got ${response.status} ${response.statusText} (${text})`);
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
    `http${fqmConnection.port === 443 ? 's' : ''}://${fqmConnection.host}:${fqmConnection.port}/query?${new URLSearchParams(
      {
        query,
        entityTypeId: entityType.id,
        fields: fields.join(','),
        limit: `${fqmConnection.limit}`,
      },
    )}`,
    {
      method: 'GET',
      headers: {
        'x-okapi-tenant': fqmConnection.tenant,
        ...token,
      },
    },
  );

  if (response.status !== 200) {
    const text = await response.text();
    if (text.includes('Token missing')) {
      await authenticate(fqmConnection);
      return runQuery(fqmConnection, entityType, query);
    }
    throw new Error(`Got ${response.status} ${response.statusText}\n${JSON.stringify(text)}`);
  }

  return ((await response.json()).content as Record<string, string>[]) ?? [];
}

export async function runQueryForValues(fqmConnection: FqmConnection, entityType: EntityType, field: string) {
  const response = await fetch(
    `http${fqmConnection.port === 443 ? 's' : ''}://${fqmConnection.host}:${fqmConnection.port}/entity-types/${entityType.id}/columns/${field}/values`,
    {
      method: 'GET',
      headers: {
        'x-okapi-tenant': fqmConnection.tenant,
        ...token,
      },
    },
  );

  if (response.status !== 200) {
    const text = await response.text();
    if (text.includes('Token missing')) {
      await authenticate(fqmConnection);
      return runQueryForValues(fqmConnection, entityType, field);
    }
    throw new Error(`Got ${response.status} ${response.statusText}\n${JSON.stringify(text)}`);
  }

  return ((await response.json()).content as { value: string; label?: string }[]) ?? [];
}

export async function install(fqmConnection: FqmConnection) {
  const response = await fetch(
    `http${fqmConnection.port === 443 ? 's' : ''}://${fqmConnection.host}:${fqmConnection.port}/_/tenant`,
    {
      method: 'POST',
      headers: {
        'x-okapi-tenant': fqmConnection.tenant,
        'content-type': 'application/json',
        ...token,
      },
      body: JSON.stringify({
        module_to: 'foo',
      }),
    },
  );

  const status = `${response.status} ${response.statusText}`;
  console.log('Installing mod-fqm-manager yielded', status);

  return { status, body: await response.text() };
}

export async function uninstall(fqmConnection: FqmConnection) {
  const response = await fetch(
    `http${fqmConnection.port === 443 ? 's' : ''}://${fqmConnection.host}:${fqmConnection.port}/_/tenant`,
    {
      method: 'POST',
      headers: {
        'x-okapi-tenant': fqmConnection.tenant,
        'content-type': 'application/json',
        ...token,
      },
      body: JSON.stringify({
        module_from: 'foo',
      }),
    },
  );

  const status = `${response.status} ${response.statusText}`;
  console.log('Uninstalling mod-fqm-manager yielded', status);

  return { status, body: await response.text() };
}

export async function migrate(
  fqmConnection: FqmConnection,
  entityTypeId: string,
  fqlQuery?: string,
  fields?: string[],
): Promise<{
  entityTypeId: string;
  fqlQuery: string;
  fields: string[];
  warnings: { description: string; type: string }[];
}> {
  const response = await fetch(
    `http${fqmConnection.port === 443 ? 's' : ''}://${fqmConnection.host}:${fqmConnection.port}/fqm/migrate`,
    {
      method: 'post',
      headers: {
        'x-okapi-tenant': fqmConnection.tenant,
        'content-type': 'application/json',
        ...token,
      },
      body: JSON.stringify({
        entityTypeId,
        fqlQuery,
        fields,
      }),
    },
  );

  if (response.status !== 200) {
    const text = await response.text();
    if (text.includes('Token missing')) {
      await authenticate(fqmConnection);
      return migrate(fqmConnection, entityTypeId, fqlQuery, fields);
    }
    throw new Error(`Got ${response.status} ${response.statusText} (${text})`);
  }

  return await response.json();
}
