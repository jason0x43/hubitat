import { readFileSync } from 'fs';
import { basename, join } from 'path';
import { CommanderStatic } from 'commander';
import { execSync } from 'child_process';

import { die, trim } from '../common';
import { Logger, createLogger } from '../log';
import {
  Manifest,
  loadManifest,
  saveManifest,
  toManifestEntry,
  toManifestSection
} from '../manifest';
import {
  getFileResources,
  hashSource,
  putResource,
  resourceDirs,
  validateId
} from '../resource';

const repoDir = '.repos';

let log: Logger;

// Setup cli ------------------------------------------------------------------

export default function init(program: CommanderStatic) {
  log = createLogger(program);

  // Push a specific resource to Hubitat
  program
    .command('push [type] [id]')
    .description('Push apps and drivers from this host to Hubitat')
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
        const localManifest = await loadManifest();

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

        await saveManifest(localManifest);
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
  const name = basename(filename);
  let source: string;

  if (!remoteManifest[type][id]) {
    const capType = `${type[0].toUpperCase()}${type.slice(1)}`;
    console.error(
      `${capType} ${name} does not exist in remote manifest; ignoring`
    );
    return false;
  }

  try {
    if (filename.indexOf(repoDir) === 0) {
      const repo = join(...filename.split('/').slice(0, 3));
      execSync('git pull', { cwd: repo });
      source = readFileSync(filename, {
        encoding: 'utf8'
      });
      // The local version is irrelevant for repo-based resources
      localRes.version = remoteRes.version;
    } else {
      source = readFileSync(join(resourceDirs[type], filename), {
        encoding: 'utf8'
      });
    }

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

    console.log(`Pushing ${type} ${filename}...`);
    const res = await putResource(type, id, localRes.version, source);
    if (res.status === 'error') {
      console.log(res);
      console.error(
        `Error pushing ${type} ${filename}: ${trim(res.errorMessage)}`
      );
      return false;
    }

    const newResource = {
      hash,
      filename,
      id: res.id,
      version: res.version
    };
    localManifest[type][res.id] = toManifestEntry(newResource);
  } catch (error) {
    if (error.code === 'ENOENT') {
      console.log(`No local script ${filename}`);
      // console.log(`No local script ${filename}, removing from manifest`);
      // delete localManifest[type][id];
    } else {
      console.error(error);
    }
  }

  return true;
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

type CodeResourceType = 'app' | 'driver';
