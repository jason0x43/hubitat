# Hubitat stuff

This repository contains the local drivers and apps that are currently loaded
onto my Hubitat, along with a script to sync the Hubitat contents with this
repo. Some of the apps and drivers are my own, some are from other sources, and
a few are ones from other sources that I cleaned up and/or modified.

## Sync script

To build the sync script:

1.  cd sync
2.  npm install
3.  npm run build

Then from the top level directory run `node sync`.

# License

Unless otherwise stated in a given file, everything in here uses the
[The Unlicense](./LICENSE).
