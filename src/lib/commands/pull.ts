import { existsSync, readFileSync, writeFileSync } from 'fs';
import { basename, join } from 'path';
import { execSync } from 'child_process';

import { die } from '../common';
import { Logger, createLogger } from '../log';
import {
  Manifest,
  getRemoteManifest,
  loadManifest,
  saveManifest,
  toManifestEntry
} from '../manifest';
import { getResource, hashSource, resourceDirs, validateId } from '../resource';
import { CommanderStatic } from 'commander';

const repoDir = '.repos';

let log: Logger;

// Setup cli ------------------------------------------------------------------

export default function init(program: CommanderStatic) {
  log = createLogger(program);

  // Pull a specific resource from Hubitat
  program
    .command('pull [type] [id]')
    .description('Pull drivers and apps from Hubitat to this host')
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
        const localManifest = await loadManifest();

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

        await saveManifest(localManifest);
      } catch (error) {
        die(error);
      }
    });
}

// Implementation -------------------------------------------------------------

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
  const localRes = localManifest[type][resource.id];

  if (!localRes) {
    console.log(`No local resource for ${type} ${resource.id}`);
    return false;
  }

  if (localRes.filename.indexOf(repoDir) === 0) {
    console.log(`Skipping github resource ${basename(localRes.filename)}`);
    return false;
  }

  const remoteRes = remoteManifest[type][resource.id];
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
 * Indicate whether a file has uncommitted changes
 */
function needsCommit(file: string) {
  if (!existsSync(file)) {
    return false;
  }
  return execSync(`git status --short ${file}`, { encoding: 'utf8' }) !== '';
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
