import { existsSync, readFileSync, writeFileSync } from 'fs';
import { join, relative } from 'path';
import { createHash } from 'crypto';
import { execSync } from 'child_process';
import fetch from 'node-fetch';
import {
  Context,
  die,
  ResourceType,
  Logger,
  createLogger,
  getResources,
  trim
} from './common';
import { CommanderStatic } from 'commander';

const manifestFile = join(__dirname, '..', 'manifest.json');
const resourceDirs = {
  app: relative(process.cwd(), join(__dirname, '..', 'apps')),
  driver: relative(process.cwd(), join(__dirname, '..', 'drivers'))
};

let program: CommanderStatic;
let hubitatHost: string;
let log: Logger;

// Setup cli ------------------------------------------------------------------

export default function init(context: Context) {
  program = context.program;
  hubitatHost = context.hubitatHost;
  log = createLogger(program);

  // Pull a specific resource from Hubitat
  program
    .command('pull [type] [id]')
    .description('Pull drivers and apps from Hubitat to this repo')
    .action(async (type, id) => {
      try {
        let rtype: CodeResourceType | undefined;
        if (type) {
          rtype = validateCodeType(type);
        }
        if (id) {
          validateId(id);
        }

        // The remote manifest will be used for filenames (if files don't exist
        // locally)
        const remoteManifest = await getRemoteManifest();
        const localManifest = loadManifest();

        if (!rtype) {
          console.log('Pulling everything...');
          await Promise.all(
            ['app', 'driver'].map(async typeStr => {
              const type = <keyof Manifest>typeStr;
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
          console.log(`Pulling all ${rtype}s...`);
          await Promise.all(
            Object.keys(remoteManifest[rtype]).map(async id => {
              updateLocalResource(
                rtype!,
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
      let rtype: keyof Manifest | undefined;

      try {
        if (type) {
          rtype = validateCodeType(type);
        }
        if (id) {
          validateId(id);
        }

        const remoteManifest = await getRemoteManifest();
        const localManifest = loadManifest();

        if (!rtype) {
          console.log('Pushing everything...');
          await Promise.all(
            ['app', 'driver'].map(async typeStr => {
              const type = <CodeResourceType>typeStr;
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
          console.log(`Pushing all ${rtype}...`);
          await Promise.all(
            Object.keys(localManifest[rtype]).map(async id => {
              await updateRemoteResource(
                rtype!,
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
}

// Implementation -------------------------------------------------------------

/**
 * Get a manifest of resources available on the Hubitat
 */
async function getRemoteManifest(type?: CodeResourceType): Promise<Manifest> {
  const manifest: Manifest = {
    app: {},
    driver: {}
  };

  if (type) {
    const resources = await getFileResources(type);
    manifest[type] = toManifestSection(resources);
  } else {
    const apps = await getFileResources('app');
    manifest.app = toManifestSection(apps);
    const drivers = await getFileResources('driver');
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
  type: CodeResourceType,
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
  type: CodeResourceType,
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
  if (res.status === 'error') {
    console.error(
      `Error pushing ${type} ${filename}: ${trim(res.errorMessage)}`
    );
    return false;
  }

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
async function getFileResources(
  resource: CodeResourceType
): Promise<FileResource[]> {
  const resources = await getResources(hubitatHost, resource);

  return Promise.all(
    resources.map(async res => {
      const { id, name, namespace } = res;
      const filename = `${namespace}-${name!
        .toLowerCase()
        .replace(/\s/g, '_')}.groovy`;

      const item = await getResource(resource, Number(id));
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
  const response = await fetch(
    `http://${hubitatHost}/${type}/ajax/code?id=${id}`
  );
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
  const response = await fetch(`http://${hubitatHost}/${type}/ajax/update`, {
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
 * Generate a SHA512 hash of a source string
 */
function hashSource(source: string) {
  const hash = createHash('sha512');
  hash.update(source);
  return hash.digest('hex');
}

function validateCodeType(type: string): CodeResourceType {
  if (/apps?/.test(type)) {
    return 'app';
  }
  if (/drivers?/.test(type)) {
    return 'driver';
  }

  die(`Invalid type "${type}"`);
  return <CodeResourceType>'';
}

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

type CodeResourceType = 'app' | 'driver';

interface ResponseResource {
  id: number;
  version: number;
  source: string;
  status: string;
  errorMessage?: string;
}

interface FileResource extends ResponseResource {
  filename: string;
  hash: string;
  type: ResourceType;
}
