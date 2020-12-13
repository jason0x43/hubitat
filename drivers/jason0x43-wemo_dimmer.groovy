/**
 * WeMo Dimmer driver
 *
 * Author: Jason Cheatham
 * Last updated: 2019-06-09, 22:48:33-0400
 *
 * Based on the original Wemo Switch driver by Juan Risso at SmartThings,
 * 2015-10-11.
 *
 * Copyright 2015 SmartThings
 *
 * Dimmer-specific information is from kris2k's wemo-dimmer-light-switch
 * driver:
 *
 * https://github.com/kris2k2/SmartThingsPublic/blob/master/devicetypes/kris2k2/wemo-dimmer-light-switch.src/wemo-dimmer-light-switch.groovy
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

metadata {
    definition(
        name: 'Wemo Dimmer',
        namespace: 'jason0x43',
        author: 'Jason Cheatham'
    ) {
        capability 'Actuator'
        capability 'Switch'
        capability 'Switch Level'
        capability 'Polling'
        capability 'Refresh'
        capability 'Sensor'

        command 'subscribe'
        command 'unsubscribe'
        command 'resubscribe'
        command 'updateAddress',            [
              [name:"IP Address", type: "STRING", description: "New IP address", constraints: []]
            ]
        command 'updatePort', 
            [
              [name:"TCP Port", type: "NUMBER", description: "New TCP port", constraints: []]
            ]
    }
}

def getDriverVersion() {
    2
}

def on() {
    log.info('Turning on')
    parent.childSetBinaryState(device, 1)
}

def off() {
    log.info('Turning off')
    parent.childSetBinaryState(device, 0)
}

def setLevel(value) {
    def binaryState = 1
    
    if (value > 0 && value <= 100) {
        binaryState = 1
    } else if (value == 0) {
        binaryState = 0
    } else {
        binaryState = 1
        value = 100
    }
    log.info("setLevel: Setting level to $value with state to $binaryState")
    parent.childSetBinaryState(device, binaryState, value)
}

def setLevel(value, duration) {
    def curValue = device.currentValue('level') 
    def tgtValue = value
    def fadeStepValue = Math.round((tgtValue - curValue)/duration)

    log.info("setLevel: Setting level to $value from $curValue with duration $duration using step value $fadeStepValue")
    // TODO, break-down duration=100 into perhaps 10 scheduled actions instead of 100
    
    // Loop through the duration counter in seconds
    for (i = 1; i <= duration; i++) {
        curValue = (curValue + fadeStepValue)
        
        // Fixup integer rounding errors on the last cycle
        if (i == duration && curValue != value) {
            curValue = value
        }
        
        // Schedule setLevel based on the duration counter
        runIn(i, setLevel_scheduledHandler, [overwrite: false, data: [value: curValue]])        
    }
}

def setLevel_scheduledHandler(data) {
    setLevel(data.value)
}

def parse(description) {
    debugLog('parse: received message')

    // A message was received, so the device isn't offline
    unschedule('setOffline')

    def msg = parseLanMessage(description)
    parent.childUpdateSubscription(msg, device)

    def result = []
    def bodyString = msg.body
    if (bodyString) {
        def body = new XmlSlurper().parseText(bodyString)

        if (body?.property?.TimeSyncRequest?.text()) {
            debugLog('parse: Got TimeSyncRequest')
            result << syncTime()
        } else if (body?.Body?.SetBinaryStateResponse?.BinaryState?.text()) {
            def rawValue = body.Body.SetBinaryStateResponse.BinaryState.text()
            debugLog("parse: Got SetBinaryStateResponse = ${rawValue}")
            result << createBinaryStateEvent(rawValue)
            
            if (body?.Body?.SetBinaryStateResponse?.brightness?.text()) {
                rawValue = body.Body.SetBinaryStateResponse.brightness?.text()
                debugLog("parse: Notify: brightness = ${rawValue}")
                result << createLevelEvent(rawValue)
            }
        } else if (body?.property?.BinaryState?.text()) {
            def rawValue = body.property.BinaryState.text()
            debugLog("parse: Notify: BinaryState = ${rawValue}")
            result << createBinaryStateEvent(rawValue)

            if (body.property.brightness?.text()) {
                rawValue = body.property.brightness?.text()
                debugLog("parse: Notify: brightness = ${rawValue}")
                result << createLevelEvent(rawValue)
            }
        } else if (body?.property?.TimeZoneNotification?.text()) {
            debugLog("parse: Notify: TimeZoneNotification = ${body.property.TimeZoneNotification.text()}")
        } else if (body?.Body?.GetBinaryStateResponse?.BinaryState?.text()) {
            def rawValue = body.Body.GetBinaryStateResponse.BinaryState.text()
            debugLog("parse: GetBinaryResponse: BinaryState = ${rawValue}")
            result << createBinaryStateEvent(rawValue)

            if (body.Body.GetBinaryStateResponse.brightness?.text()) {
                rawValue = body.Body.GetBinaryStateResponse.brightness?.text()
                debugLog("parse: GetBinaryResponse: brightness = ${rawValue}")
                result << createLevelEvent(rawValue)
            }
        }
    }

    result
}

def poll() {
    log.info('Polling')

    // Schedule a call to flag the device offline if no new message is received
    if (device.currentValue('switch') != 'offline') {
        runIn(10, setOffline)
    }

    parent.childGetBinaryState(device)
}

def refresh() {
    log.info('Refreshing')
    [
        resubscribe(),
        syncTime(),
        poll()
    ]
}

def resubscribe() {
    log.info('Resubscribing')

    // Schedule a subscribe check that will run after the resubscription should
    // have completed
    runIn(10, subscribeIfNecessary)

    parent.childResubscribe(device)
}

def setOffline() {
    sendEvent(
        name: 'switch',
        value: 'offline',
        descriptionText: 'The device is offline'
    )
}

def subscribe() {
    log.info('Subscribing')
    parent.childSubscribe(device)
}

def subscribeIfNecessary() {
    parent.childSubscribeIfNecessary(device)
}

def unsubscribe() {
    log.info('Unsubscribing')
    parent.childUnsubscribe(device)
}

def updated() {
    log.info('Updated')
    refresh()
}

private createBinaryStateEvent(rawValue) {
    def value = ''
    
    // Properly interpret our rawValue
    if (rawValue == '1') {
        value = 'on'
    } else if (rawValue == '0') {
        value = 'off'
    } else {
        // Sometimes, wemo returns us with rawValue=error, so we do nothing
        debugLog("parse: createBinaryStateEvent: rawValue = ${rawValue} : Invalid! Not raising any events")
        return
    }
    
    // Raise the switch state event
    createEvent(
        name: 'switch',
        value: value,
        descriptionText: "Switch is ${value} : ${rawValue}"
    )
}

private createLevelEvent(rawValue) {
    def value = "$rawValue".toInteger() // rawValue is always an integer from 0 to 100
    createEvent(
        name: 'level',
        value: value,
        descriptionText: "Level is ${value}"
    )
}

private debugLog(message) {
    if (parent.debugLogging) {
        log.debug(message)
    }
}

private syncTime() {
    parent.childSyncTime(device)
}

def updatePort(port) {
    parent.childUpdatePort(device, port)
}

def updateAddress(ip) {
    parent.childUpdateIP(device, ip)
}
