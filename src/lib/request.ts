import fetch from 'node-fetch';

let hubitatHost: string;

export function hubitatFetch(path: string, init?: fetch.RequestInit) {
  return fetch(`http://${hubitatHost}${path}`, init);
}

export function getHost() {
  return hubitatHost;
}

export function setHost(host: string) {
  hubitatHost = host;
}
