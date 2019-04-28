#!/usr/bin/env node

require('source-map-support').install();
require('dotenv-safe').config();

import program from 'commander';

import initEvents from './lib/commands/events';
import initInfo from './lib/commands/info';
import initInstall from './lib/commands/install';
import initList from './lib/commands/list';
import initLog from './lib/commands/log';
import initPull from './lib/commands/pull';
import initPush from './lib/commands/push';
import initRun from './lib/commands/run';

program.description('Interact with hubitat').option('-v, --verbose');

initEvents(program);
initInfo(program);
initInstall(program);
initList(program);
initLog(program);
initPull(program);
initPush(program);
initRun(program);

program.parse(process.argv);

if (!process.argv.slice(2).length) {
  program.outputHelp();
}
