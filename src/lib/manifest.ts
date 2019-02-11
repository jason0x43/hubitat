import { promises } from 'fs';
import { join } from 'path';
import { CodeResourceType, FileResource, getFileResources } from './resource';

const { readFile, writeFile } = promises;
const manifestFile = join(__dirname, '..', '..', 'manifest.json');

/**
 * Get a manifest of resources available on the Hubitat
 */
export async function getRemoteManifest(
  type?: CodeResourceType
): Promise<Manifest> {
  const manifest: Manifest = {
    app: {},
    driver: {}
  };

  if (type) {
    console.log(`Loading remote manifest for ${type}s...`);
    const resources = await getFileResources(type);
    manifest[type] = toManifestSection(resources);
    console.log(`Loaded ${Object.keys(resources).length} entries`);
  } else {
    console.log('Loading remote manifest for all resource types...');
    const apps = await getFileResources('app');
    const numApps = Object.keys(apps).length;
    manifest.app = toManifestSection(apps);
    console.log(`Loaded ${numApps} app${numApps === 1 ? '' : 's'}`);

    const drivers = await getFileResources('driver');
    const numDrivers = Object.keys(drivers).length;
    manifest.driver = toManifestSection(drivers);
    console.log(`Loaded ${numDrivers} driver${numDrivers === 1 ? '' : 's'}`);
  }

  return manifest;
}

/**
 * Load the current manifest file
 */
export async function loadManifest(): Promise<Manifest> {
  try {
    const data = await readFile(manifestFile, { encoding: 'utf8' });
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
export async function saveManifest(manifest: Manifest) {
  return await writeFile(manifestFile, JSON.stringify(manifest, null, '  '));
}

/**
 * Create a manifest entry representing a FileResource
 */
export function toManifestEntry(resource: ManifestEntry) {
  return {
    filename: resource.filename,
    id: resource.id,
    version: resource.version,
    hash: resource.hash
  };
}

/**
 * Create a manifest section representing an array of FileResources
 */
export function toManifestSection(resources: FileResource[]) {
  return resources.reduce(
    (all, res) => ({
      ...all,
      [res.id]: toManifestEntry(res)
    }),
    <ManifestResources>{}
  );
}

export interface Manifest {
  app: ManifestResources;
  driver: ManifestResources;
}

export interface ManifestResources {
  [id: number]: ManifestEntry;
}

export interface ManifestEntry {
  id: number;
  filename: string;
  version: number;
  hash: string;
}
