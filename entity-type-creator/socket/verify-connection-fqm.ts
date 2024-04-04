import { FqmConnection } from '@/types';

export default async function verifyFqmConnection(fqmConnection: FqmConnection) {
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