import { existsSync, promises } from 'fs';
import { join } from 'path';
import { createHash } from 'crypto';
import cheerio from 'cheerio';
import { execSync } from 'child_process';
import { CommanderStatic } from 'commander';

import { die, simpleEncode } from '../common';
import { loadManifest, saveManifest, toManifestEntry } from '../manifest';
import { hubitatFetch } from '../request';
import { ResourceType, getFilename, getResources } from '../resource';

const { mkdir, readFile } = promises;
const repoDir = '.repos';

// Setup cli ------------------------------------------------------------------

export default function init(program: CommanderStatic) {
  // Install a script from a github repo
  program
    .command('install <type> <path>')
    .description(
      'Install a resource from a GitHub path ' +
        '(git:org/repo/file.groovy) or local file path'
    )
    .action(async (type, path) => {
      if (!/[^/]+\/.*\.groovy$/.test(path)) {
        die('path must have format org/repo/path/to/file.groovy');
      }

      let filename: string;
      let isGithubResource = false;

      if (/^git:/.test(path)) {
        isGithubResource = true;
        const gitPath = path.slice(4);
        const parts = gitPath.split('/');
        const orgPath = join(repoDir, parts[0]);
        await mkdirp(orgPath);

        const repoPath = join(orgPath, parts[1]);
        if (!existsSync(repoPath)) {
          const repo = parts.slice(0, 2).join('/');
          execSync(`git clone git@github.com:${repo}`, { cwd: orgPath });
        }

        filename = join(repoDir, gitPath);
      } else {
        filename = path;
      }

      try {
        const rtype = validateCodeType(type);
        const localManifest = await loadManifest();

        console.log(`Installing ${filename}...`);
        await createRemoteResource(
          rtype,
          filename,
          localManifest,
          isGithubResource
        );
        saveManifest(localManifest);
      } catch (error) {
        die(error);
      }
    });
}

// Implementation -------------------------------------------------------------

/**
 * Create a remote resource. This should return a new version number which will
 * be added to the manifest.
 */
async function createRemoteResource(
  type: CodeResourceType,
  filename: string,
  localManifest: Manifest,
  isGithubResource = false
): Promise<boolean> {
  const source = await readFile(filename, {
    encoding: 'utf8'
  });

  const hash = hashSource(source);
  console.log(`Creating ${type} ${filename}...`);
  const newRes = await postResource(type, source);
  let newEntry: ManifestEntry;

  if (isGithubResource) {
    newEntry = {
      hash,
      filename,
      id: newRes.id,
      version: 1
    };
  } else {
    const resources = await getResources(type);
    const resource = resources.find(res => res.id === newRes.id)!;
    newEntry = {
      hash,
      filename: getFilename(resource),
      ...newRes
    };
  }

  localManifest[type][newRes.id] = toManifestEntry(newEntry);

  return true;
}

/**
 * Create a specific resource (driver or app)
 */
async function postResource(
  type: ResourceType,
  source: string
): Promise<CreateResource> {
  const path = `/${type}/save`;
  const response = await hubitatFetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: simpleEncode({ id: '', version: '', source })
  });

  if (response.status !== 200) {
    throw new Error(`Error creating ${type}: ${response.statusText}`);
  }

  const html = await response.text();
  const $ = cheerio.load(html);

  const errors = $('#errors');
  const errorText = errors.text().replace(/×/, '').trim();
  if (errorText) {
    throw new Error(`Error creating ${type}: ${errorText}`);
  }

  const form = $('form[name="editForm"]');
  const id = $(form)
    .find('input[name="id"]')
    .val();
  const version = $(form)
    .find('input[name="version"]')
    .val();

  return {
    id: Number(id),
    version: Number(version)
  };
}

/**
 * Make a directory and its parents
 */
async function mkdirp(dir: string) {
  const parts = dir.split('/');
  let path = '.';
  while (parts.length > 0) {
    path = join(path, parts.shift()!);
    if (!existsSync(path)) {
      await mkdir(path);
    }
  }
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
  id: number;
  filename: string;
  version: number;
  hash: string;
}

type CodeResourceType = 'app' | 'driver';

interface CreateResource {
  id: number;
  version: number;
}
