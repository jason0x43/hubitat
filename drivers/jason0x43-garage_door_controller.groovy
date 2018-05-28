/**
 * Garage Door Controller
 *
 * Author: Jason Cheatham <j.cheatham@gmail.com>
 * Last updated: 2018-05-28, 09:39:51-0400
 */

metadata {
    definition(name: 'Garage Door Controller', namespace: 'jason0x43', author: 'jason0x43') {
        capability 'Actuator'
        capability 'Door Control'
        capability 'Garage Door Control'
        capability 'Sensor'
        capability 'Refresh'
    }
}

def open() {
    log.debug 'open()'
    parent.open()
}

def close() {
    log.debug 'close()'
    parent.close()
}

def refresh() {
    log.debug 'refresh()'
    parent.refresh()
}

def setDoorState(newState) {
    if (newState.door != device.currentDoor) {
        log.debug "setDoorState(${newState})"
        sendEvent(name: 'door', value: newState.door)
    }
}
