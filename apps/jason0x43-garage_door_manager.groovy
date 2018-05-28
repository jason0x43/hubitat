/**
 * Garage Door Manager
 *
 * Author: Jason Cheatham <j.cheatham@gmail.com>
 * Last updated: 2018-05-28, 10:39:15-0400
 */

definition(
    name: 'Garage Door Manager',
    namespace: 'jason0x43',
    author: 'j.cheatham@gmail.com',
    description: 'Manage a composite garage door opener',
    singleInstance: true,
    iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
    iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png'
)

preferences {
    page(name: 'mainPage')
}

def mainPage() {
    def canInstall = true

    if (
        (contactSensor == null && threeAxisSensor == null)
        || relay == null
    ) {
        canInstall = false
    }

    dynamicPage(name: 'mainPage', uninstall: true, install: canInstall) {
        section('Relay') {
            paragraph(
                'The relay is the actuator that actually controls the door.' +
                'This device MUST be configured to auto-shutoff.'
            )

            input(
                name: 'relay',
                type: 'capability.switch',
                title: 'Garage Door Relay',
                submitOnChange: true,
                required: true
            )
        }

        section('State Sensor') {
            paragraph(
                'A contact or acceleration sensor is used to tell if the ' +
                'door is open or closed. One of these is required.'
            )

            input(
                name: 'contactSensor',
                type: 'capability.contactSensor',
                title: 'Contact Sensor',
                submitOnChange: true,
                required: false
            )
            
            input(
                name: 'threeAxisSensor',
                type: 'capability.threeAxis',
                title: 'Three Axis Sensor',
                submitOnChange: true,
                required: false
            )

            if (threeAxisSensor) {
                paragraph "Current value: ${threeAxisSensor.currentThreeAxis}"
                input(
                    name: 'closedX',
                    type: 'number',
                    title: 'Closed x',
                    required: true
                )
                input(
                    name: 'closedY',
                    type: 'number',
                    title: 'Closed y',
                    required: true
                )
                input(
                    name: 'closedZ',
                    type: 'number',
                    title: 'Closed z',
                    required: true
                )
            }
        }

        section('Activity Sensor') {
            paragraph(
                'This sensor can be used to tell if the door is in motion.'
            )

            input(
                name: 'accelerationSensor',
                type: 'capability.accelerationSensor',
                title: 'Acceleration Sensor',
                submitOnChange: true,
                required: false
            )
        }
    }
}

def installed() {
    init()
}

def updated() {
    log.trace('Unsubscribing...')
    unsubscribe()
    init()
}

def close() {
    if (state.current == 'opening') {
        // stop and restart
        relay.on()
        runIn(5, pushRelay)
    } else if (state.current == 'open') {
        // start
        relay.on()
    }
}

def handleAcceleration(evt) {
    def value = evt.value
    log.trace "Handling acceleration event: ${value}"

    updateState([acceleration: value])
    state.previousAcceleration = value
}

def handleContact(evt) {
    def value = evt.value
    log.trace "Handling contact event: ${value}"

    updateState([contact: value])
    state.previousContact = value
}

def handleThreeAxis(evt) {
    def value = parseThreeAxis(evt.value)
    log.trace "Handling theeAxis event: ${value}"

    updateState([threeAxis: value])
    state.previousThreeAxis = value

    if (evt) {
        // If we see a three axis event, it means the sensor is moving. Call a
        // handler to indicate that we've stopped a few seconds after the last
        // threeAxis event.
        runIn(5, threeAxisStop)
    }
}

def open() {
    if (state.current == 'closing') {
        // stop and restart
        relay.on()
            runIn(5, pushRelay)
    } else if (state.current == 'closed') {
        // start
        relay.on()
    }
}

def pushRelay() {
    relay.on()
}

def refresh() {
    try {
        accelerationSensor.refresh()
    } catch (error) {
        // ignore
    }
    try {
        contactSensor.refresh()
    } catch (error) {
        // ignore
    }
    try {
        threeAxisSensor.refresh()
    } catch (error) {
        // ignore
    }
    try {
        relay.refresh()
    } catch (error) {
        // ignore
    }
}

def threeAxisStop() {
    if (isMoving()) {
        handleAcceleration(value: 'inactive')
    }
}

private getController() {
    def children = getChildDevices()
    return children.size == 0 ? null : children[0]
}

private getDistance(a, b) {
    def x2 = (a.x - b.x) * (a.x - b.x)
    def y2 = (a.y - b.y) * (a.y - b.y)
    def z2 = (a.z - b.z) * (a.z - b.z)
    def dist = Math.sqrt(x2 + y2 + z2)
    return dist
}

private getThreeAxisState(current) {
    if (!current) {
        current = parseThreeAxis(threeAxisSensor.currentThreeAxis)
    }
    // log.trace "Getting threeAxis state for current value ${current}"

    def closed = [x: closedX, y: closedY, z: closedZ];
    return getDistance(current, closed) < 10 ? 'closed' : 'open'
}

// Get the direction based on three-axis info. This is somewhat unreliable.
private getThreeAxisDirection(current) {
    def last = state.previousThreeAxis
    if (last == null) {
        return 'unknown'
    }

    if (!current) {
        current = parseThreeAxis(threeAxisSensor.currentThreeAxis)
    }
    log.trace "Getting threeAxis direction for current value ${current}"

    def closed = [x: closedX, y: closedY, z: closedZ];

    // log.trace 'Getting last dist from open'
    def lastDist = getDistance(last, closed);

    // log.trace 'Getting current dist from open'
    def currentDist = getDistance(current, closed);

    // Only consider distances over 10 units to prevent bounce-related
    // direction changes
    if ((lastDist - currentDist).abs() > 10) {
        if (lastDist < currentDist) {
            log.trace "Opening (${lastDist}, ${currentDist})"
            return 'opening'
        }

        if (lastDist > currentDist) {
            log.trace "Closing (${lastDist}, ${currentDist})"
            return 'closing'
        }
    }

    return 'unknown';
}

private init() {
    if (!getController()) {
        log.trace "Creating child device..."
        def child = addChildDevice(
            'jason0x43',
            'Garage Door Controller',
            'garage-door-controller',
            null,
            [ 'label':  'Garage door' ]
        )
        log.trace "Created ${child.displayName} with id ${child.id}"
    }

    if (accelerationSensor) {
        subscribe(accelerationSensor, 'acceleration', handleAcceleration);
        log.trace 'Subscribed to acceleration'
    }

    if (contactSensor) {
        subscribe(contactSensor, 'contact', handleContact);
        log.trace 'Subscribed to contact'
    }

    if (threeAxisSensor) {
        subscribe(threeAxisSensor, 'threeAxis', handleThreeAxis);
        log.trace 'Subscribed to threeAxis'
    }
}

private isMoving() {
    return state.current == 'closing' || state.current == 'opening';
}

private parseThreeAxis(val) {
    def matcher = val =~ /\[x:([^,]+),\s*y:([^,]+),\s*z:([^,]+)\]/
    if (matcher.matches()) {
        def x = matcher[0][1].toInteger()
        def y = matcher[0][2].toInteger()
        def z = matcher[0][3].toInteger()
        return [x: x, y: y, z: z]
    }
}

private updateState(evt) {
    log.trace "updateState(${evt})"

    if (evt.contact) {
        if (evt.contact == 'closed') {
            state.current = 'closed'
        } else {
            state.current = 'opening'
        }
    } else if (evt.acceleration) {
        if (evt.acceleration == 'active') {
            // Door has started to move; guess the current direction based on
            // the current and last states
            if (state.current == 'open') {
                if (state.lastDirection == 'closing') {
                    state.current = 'opening'
                } else {
                    state.current = 'closing'
                }
            } else {
                state.current = 'opening'
            }
        } else {
            // Door has stopped. Use three-axis value if we have it, else wait
            // for contact event.
            if (threeAxisSensor) {
                state.current = getThreeAxisState()
            }
        }
    } else {
        // Don't pay attention to three-axis movement values if there is a contact
        // sensor and it's state is 'closed' or if there is an acceleration
        // sensor and it's state is 'inactive'.
        if (
            (!contactSensor || contactSensor.currentContact == 'open') &&
            (!accelerationSensor || accelerationSensor.currentAcceleration == 'active')
        ) {
            def dir = getThreeAxisDirection(evt.threeAxis)
            if (
                dir == 'unknown' &&
                !(state.current == 'opening' || state.current == 'closing')
            ) {
                state.current = dir
            }
        }
    }

    if (state.current == 'opening' || state.current == 'closing') {
        state.lastDirection = state.current;
    }

    getController().setDoorState(door: state.current)
}
