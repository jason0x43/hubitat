import fetch from 'node-fetch';

const hubitatHost = process.env.HUBITAT_HOST!;
const makerApiId = process.env.MAKER_API_ID!;
const makerApiToken = process.env.MAKER_API_TOKEN!;

export function hubitatFetch(path: string, init?: fetch.RequestInit) {
  return fetch(`http://${hubitatHost}${path}`, init);
}

export function makerFetch(path: string) {
  return fetch(
    `http://${hubitatHost}/apps/api/${makerApiId}${path}?access_token=${makerApiToken}`
  );
}

export function getHost() {
  return hubitatHost;
}
