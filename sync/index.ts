#!/usr/bin/env node

import { existsSync, readFileSync, writeFileSync } from 'fs';
import { join, relative } from 'path';
import { createHash } from 'crypto';
import { execSync } from 'child_process';
import fetch from 'node-fetch';
import * as cheerio from 'cheerio';
import * as program from 'commander';

const Table = require('easy-table');
require('dotenv-safe').config();

const hubitat = process.env.HUBITAT;
const manifestFile = join(__dirname, '..', 'manifest.json');
const resourceDirs = {
  app: relative(process.cwd(), join(__dirname, '..', 'apps')),
  driver: relative(process.cwd(), join(__dirname, '..', 'drivers'))
};
const tableSelectors = {
  app: '#hubitapps-table tbody .app-row',
  driver: '#devicetype-table tbody .device-row'
};
const linkProcessors = {
  app: link => link.attr('title'),
  driver: link => link.text()
};

// Setup cli ------------------------------------------------------------------

program
  .description('Sync apps and drivers with hubitat')
  .option('-v, --verbose');

program
  .command('list [type]')
  .description(`List items on Hubitat, optional of type 'drivers' or 'apps'`)
  .action(async type => {
    type = type ? validateType(type) : type;
    const t = new Table();

    if (!type || type == 'driver') {
      const drivers = await getResources('driver');
      drivers.forEach(driver => {
        addResourceRow(t, driver, type ? undefined : 'driver');
      });
    }
    if (!type || type == 'app') {
      const apps = await getResources('app');
      apps.forEach(app => {
        addResourceRow(t, app, type ? undefined : 'app');
      });
    }

    console.log(t.toString());
  });

function addResourceRow(
  t: typeof Table,
  resource: FileResource,
  type?: ResourceType
) {
  if (type) {
    t.cell('type', type);
  }
  t.cell('id', resource.id, Table.number());
  t.cell('version', resource.version, Table.number());
  t.cell('filename', resource.filename);
  t.newRow();
}

// Pull a specific resource from Hubitat
program
  .command('pull [type] [id]')
  .description('Pull items from Hubitat to this repo')
  .action(async (type, id) => {
    try {
      if (type) {
        type = validateType(type);
      }
      if (id) {
        validateId(id);
      }

      // The remote manifest will be used for filenames (if files don't exist
      // locally)
      const remoteManifest = await getRemoteManifest();
      const localManifest = loadManifest();

      if (!type) {
        console.log('Pulling everything...');
        await Promise.all(
          ['app', 'driver'].map(async (type: ResourceType) => {
            await Promise.all(
              Object.keys(remoteManifest[type]).map(async id => {
                await updateLocalResource(
                  type,
                  Number(id),
                  localManifest,
                  remoteManifest
                );
              })
            );
          })
        );
      } else if (!id) {
        console.log(`Pulling all ${type}s...`);
        await Promise.all(
          Object.keys(remoteManifest[type]).map(async id => {
            updateLocalResource(
              type,
              Number(id),
              localManifest,
              remoteManifest
            );
          })
        );
      } else {
        console.log(`Pulling ${type}:${id}...`);
        updateLocalResource(type, id, localManifest, remoteManifest);
      }

      saveManifest(localManifest);
    } catch (error) {
      die(error);
    }
  });

// Push a specific resource to Hubitat
program
  .command('push [type] [id]')
  .description('Push apps and drivers from this repo to Hubitat')
  .action(async (type, id) => {
    try {
      if (type) {
        type = validateType(type);
      }
      if (id) {
        validateId(id);
      }

      const remoteManifest = await getRemoteManifest();
      const localManifest = loadManifest();

      if (!type) {
        console.log('Pushing everything...');
        await Promise.all(
          ['app', 'driver'].map(async (type: ResourceType) => {
            await Promise.all(
              Object.keys(localManifest[type]).map(async id => {
                await updateRemoteResource(
                  type,
                  Number(id),
                  localManifest,
                  remoteManifest
                );
              })
            );
          })
        );
      } else if (!id) {
        console.log(`Pushing all ${type}...`);
        await Promise.all(
          Object.keys(localManifest[type]).map(async id => {
            await updateRemoteResource(
              type,
              Number(id),
              localManifest,
              remoteManifest
            );
          })
        );
      } else {
        console.log(`Pushing ${type}:${id}...`);
        await updateRemoteResource(type, id, localManifest, remoteManifest);
      }

      saveManifest(localManifest);
    } catch (error) {
      die(error);
    }
  });

program.parse(process.argv);

if (!process.argv.slice(2).length) {
  program.outputHelp();
}

// Implementation -------------------------------------------------------------

/**
 * Get a manifest of resources available on the Hubitat
 */
async function getRemoteManifest(type?: ResourceType): Promise<Manifest> {
  const manifest: Manifest = {
    app: {},
    driver: {}
  };

  if (type) {
    const resources = await getResources(type);
    manifest[type] = toManifestSection(resources);
  } else {
    const apps = await getResources('app');
    manifest.app = toManifestSection(apps);
    const drivers = await getResources('driver');
    manifest.driver = toManifestSection(drivers);
  }

  return manifest;
}

/**
 * Update a local resource with a remote resource. This saves a local copy of
 * the resource and updates the manifest. If the remote resource is newer than
 * the local resource, it will overwrite the local resource. If the local
 * resource has been edited, it will need to be committed before a pull can
 * complete.
 *
 * After a pull, any files that differ between the remote and local will result
 * in unstaged changes in the local repo.
 */
async function updateLocalResource(
  type: ResourceType,
  id: number,
  localManifest: Manifest,
  remoteManifest: Manifest
): Promise<boolean> {
  const resource = await getResource(type, id);
  const remoteRes = remoteManifest[type][resource.id];
  const localRes = localManifest[type][resource.id];
  const filename = join(resourceDirs[type], remoteRes.filename);

  if (localRes) {
    const source = readFileSync(filename, { encoding: 'utf8' });
    const sourceHash = hashSource(source);
    // If the local has changed from the last time it was synced with Hubitat
    // *and* it hasn't been committed, don't update
    if (sourceHash !== localRes.hash && needsCommit(filename)) {
      console.log(`Skipping ${filename}; please commit first`);
      return false;
    }
  }

  if (localRes && remoteRes.hash === localRes.hash) {
    log(`Skipping ${filename}; no changes`);
    return true;
  }

  console.log(`Updating ${type} ${filename}`);
  writeFileSync(join(resourceDirs[type], remoteRes.filename), resource.source);

  const hash = hashSource(resource.source);
  const newResource = { type, hash, filename: remoteRes.filename, ...resource };
  localManifest[type][resource.id] = toManifestEntry(newResource);

  return true;
}

/**
 * Update a remote resource. This should return a new version number which will
 * be added to the manifest.
 */
async function updateRemoteResource(
  type: ResourceType,
  id: number,
  localManifest: Manifest,
  remoteManifest: Manifest
): Promise<boolean> {
  const localRes = localManifest[type][id];
  const remoteRes = remoteManifest[type][id];
  const filename = localRes.filename;
  const source = readFileSync(join(resourceDirs[type], filename), {
    encoding: 'utf8'
  });

  const hash = hashSource(source);
  if (hash === localRes.hash) {
    // File hasn't changed -- don't push
    log(`${filename} hasn't changed; not pushing`);
    return true;
  }

  if (localRes.version !== remoteRes.version) {
    console.error(`${type} ${filename} is out of date; pull first`);
    return false;
  }

  console.log(`Pushing ${filename}...`);
  const res = await putResource(type, id, localRes.version, source);
  const newResource = {
    hash,
    filename,
    version: res.version
  };
  localManifest[type][res.id] = toManifestEntry(newResource);

  return true;
}

/**
 * Indicate whether a file has uncommitted changes
 */
function needsCommit(file: string) {
  if (!existsSync(file)) {
    return false;
  }
  return execSync(`git status --short ${file}`, { encoding: 'utf8' }) !== '';
}

/**
 * Display a message and quit
 */
function die(message: string | Error) {
  if (typeof message === 'string') {
    console.error(message);
  } else {
    console.error(message.stack);
  }
  process.exit(1);
}

/**
 * Verify that a type variable is a ResourceType
 */
function validateType(type: string): ResourceType {
  if (/apps?/.test(type)) {
    return 'app';
  } else if (/drivers?/.test(type)) {
    return 'driver';
  } else {
    die(`Invalid type "${type}"`);
  }
}

/**
 * Verify that an id is in a valid format
 */
function validateId(id: string): number {
  const numId = Number(id);
  if (isNaN(Number(id))) {
    die('ID must be a number');
  }

  return numId;
}

/**
 * Return a string representation of a FileResource
 */
function resourceToString(item: FileResource) {
  return `${item.type}: ${item.filename} [${item.version}]`;
}

/**
 * Create a manifest section representing an array of FileResources
 */
function toManifestSection(resources: FileResource[]) {
  return resources.reduce(
    (all, res) => ({
      ...all,
      [res.id]: toManifestEntry(res)
    }),
    <ManifestResources>{}
  );
}

/**
 * Create a manifest entry representing a FileResource
 */
function toManifestEntry(resource: ManifestEntry) {
  return {
    filename: resource.filename,
    version: resource.version,
    hash: resource.hash
  };
}

/**
 * Get a resource list from Hubitat
 */
async function getResources(resource: ResourceType): Promise<FileResource[]> {
  const response = await fetch(`${hubitat}/${resource}/list`);
  const html = await response.text();
  const $ = cheerio.load(html);
  const selector = tableSelectors[resource];
  const getText = linkProcessors[resource];

  const rows = $(selector).toArray();
  return Promise.all(
    rows.map(async elem => {
      const row = $(elem);
      const id = row.data(`${resource}-id`);
      if (!id) {
        throw new Error(`No ID in row ${row.text()}`);
      }

      const link = $(row.find('td')[0]).find('a');
      const text = getText(link);
      const [namespace, name] = text.split(':').map(trim);
      const filename = `${namespace}-${name
        .toLowerCase()
        .replace(/\s/g, '_')}.groovy`;

      const item = await getResource(resource, id);
      const hash = hashSource(item.source);
      return { filename, hash, type: resource, ...item };
    })
  );
}

/**
 * Retrieve a specific resource (driver or app)
 */
async function getResource(
  type: ResourceType,
  id: number
): Promise<ResponseResource> {
  const response = await fetch(`${hubitat}/${type}/ajax/code?id=${id}`);
  if (response.status !== 200) {
    throw new Error(`Error getting ${type} ${id}: ${response.statusText}`);
  }
  return await response.json<ResponseResource>();
}

/**
 * Store a specific resource (driver or app)
 */
async function putResource(
  type: ResourceType,
  id: number,
  version: number,
  source: string
): Promise<ResponseResource> {
  const response = await fetch(`${hubitat}/${type}/ajax/update`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: simpleEncode({ id, version, source })
  });
  if (response.status !== 200) {
    throw new Error(`Error putting ${type} ${id}: ${response.statusText}`);
  }
  return response.json<ResponseResource>();
}

/**
 * Encode a JS object to x-www-form-urlencoded format
 */
function simpleEncode(value: any, key?: string, list?: string[]) {
  list = list || [];
  if (typeof value === 'object') {
    for (let k in value) {
      simpleEncode(value[k], key ? `${key}[${k}]` : k, list);
    }
  } else {
    list.push(`${key}=${encodeURIComponent(value)}`);
  }
  return list.join('&');
}

/**
 * Trim whitespace from either end of a string
 */
function trim(str: string) {
  return str.replace(/^\s+/, '').replace(/\s+$/, '');
}

/**
 * Load the current manifest file
 */
function loadManifest(): Manifest {
  try {
    const data = readFileSync(manifestFile, { encoding: 'utf8' });
    if (data) {
      return JSON.parse(data);
    }
  } catch (error) {
    if (error.code !== 'ENOENT') {
      throw error;
    }
  }

  return {
    app: {},
    driver: {}
  };
}

/**
 * Save the given manifest, overwriting the current manifest
 */
function saveManifest(manifest: Manifest) {
  return writeFileSync(manifestFile, JSON.stringify(manifest, null, '  '));
}

/**
 * Indicate whether a local manifest is up to date with a remote
 */
function isUpToDate(local: Manifest, remote: Manifest): boolean {
  return (
    isSectionUpToDate(local.app, remote.app) &&
    isSectionUpToDate(local.driver, remote.driver)
  );
}

/**
 * Indicate whether a section of a local manifest is up to date with the
 * corresponding section of a remote manifest
 */
function isSectionUpToDate(
  local: ManifestResources,
  remote: ManifestResources
): boolean {
  const localKeys = Object.keys(local);
  const remoteKeys = Object.keys(remote);
  if (localKeys.length !== localKeys.length) {
    return false;
  }
  for (let i = 0; i < localKeys.length; i++) {
    if (remoteKeys.indexOf(localKeys[i]) === -1) {
      return false;
    }
    const key = localKeys[i];
    if (local[key].version === remote[key].version) {
      return false;
    }
  }
  return true;
}

/**
 * Generate a SHA512 hash of a source string
 */
function hashSource(source: string) {
  const hash = createHash('sha512');
  hash.update(source);
  return hash.digest('hex');
}

/**
 * Debug log
 */
function log(message) {
  if (program.verbose) {
    console.log(message);
  }
}

interface ResponseResource {
  id: number;
  version: number;
  source: string;
  status: string;
}

interface FileResource extends ResponseResource {
  filename: string;
  hash: string;
  type: ResourceType;
}

export type ResourceType = 'driver' | 'app';

interface Manifest {
  app: ManifestResources;
  driver: ManifestResources;
}

interface ManifestResources {
  [id: number]: ManifestEntry;
}

interface ManifestEntry {
  filename: string;
  version: number;
  hash: string;
}
