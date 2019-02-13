import fetch from 'node-fetch';

const hubitatHost = process.env.HUBITAT_HOST!;

export function hubitatFetch(path: string, init?: fetch.RequestInit) {
  return fetch(`http://${hubitatHost}${path}`, init);
}

export function getHost() {
  return hubitatHost;
}
