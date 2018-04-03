#!/usr/bin/env node

import * as program from 'commander';
import { Context } from './common';
import initSync from './sync';
import initLog from './log';
import initList from './list';
import initInfo from './info';

require('dotenv-safe').config();

program.description('Interact with hubitat').option('-v, --verbose');

const context: Context = {
  program,
  hubitatHost: process.env.HUBITAT_HOST!
};

initSync(context);
initLog(context);
initList(context);
initInfo(context);

program.parse(process.argv);

if (!process.argv.slice(2).length) {
  program.outputHelp();
}
