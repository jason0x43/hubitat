# Hubitat stuff

This repository contains the local drivers and apps that are currently loaded
onto my Hubitat, along with a script to interact with the Hubitat. Some of the
apps and drivers are my own, some are from other sources, and a few are ones
from other sources that I cleaned up and/or modified.

## Nest Integration

This repo contains an integration app and driver to control Nest thermostats.
See the [Nest Integration app source](./apps/jason0x43-nest.groovy) for setup
instructions.

## hubitat script

The `hubitat` application is TypeScript application that allows some degree of
interaction with the Hubitat through the command line. The `hubitat` script in
the root of this repo is a small bash script that will run the `hubitat`
application, rebuilding it as necessary.

At the moment, `hubitat` supports 4 commands:

* **list** - List apps, devices, or drivers
* **log** - Log messages emitted by apps or devices
* **pull** - Pull code from the hubitat into the local repo
* **push** - Push code from the local repo to the hubitat

# License

Unless otherwise stated in a given file, everything in here uses the
[The Unlicense](./LICENSE).
