#!/usr/bin/env node

import program from 'commander';

import { setHost } from './lib/request';
import initPush from './lib/commands/push';
import initPull from './lib/commands/pull';
import initInstall from './lib/commands/install';
import initLog from './lib/commands/log';
import initList from './lib/commands/list';
import initInfo from './lib/commands/info';
import initRun from './lib/commands/run';

require('source-map-support').install();
require('dotenv-safe').config();

program.description('Interact with hubitat').option('-v, --verbose');

setHost(process.env.HUBITAT_HOST!);

initInstall(program);
initPush(program);
initPull(program);
initLog(program);
initList(program);
initInfo(program);
initRun(program);

program.parse(process.argv);

if (!process.argv.slice(2).length) {
  program.outputHelp();
}
