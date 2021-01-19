/**
 * WeMo Insight Switch driver
 *
 * Author: Jason Cheatham
 * Last updated: 2021-01-18, 20:01:12-0500
 *
 * Based on the original Wemo Switch driver by Juan Risso at SmartThings,
 * 2015-10-11.
 *
 * Copyright 2015 SmartThings
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
        name: 'Wemo Insight Switch',
        namespace: 'jason0x43',
        author: 'Jason Cheatham'
    ) {
        capability 'Actuator'
        capability 'Switch'
        capability 'Polling'
        capability 'Refresh'
        capability 'Sensor'
        capability 'Power Meter'
        capability 'Energy Meter'

        command 'subscribe'
        command 'unsubscribe'
        command 'resubscribe'
    }

    preferences {
        input(
            name: 'ipAddress',
            type: 'string',
            title: 'IP address',
            defaultValue: hexToIp(getDataValue('ip') ?: "00000000")
        )
        input(
            name: 'ipPort',
            type: 'number',
            title: 'IP port',
            defaultValue: HexUtils.hexStringToInt(getDataValue('port') ?: "0")
        )
    }
}

def getDriverVersion() {
    3
}

def on() {
    log.info('Turning on')
    parent.childSetBinaryState(device, '1')
}

def off() {
    log.info('Turning off')
    parent.childSetBinaryState(device, '0')
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
            debugLog("parse: Got SetBinaryStateResponse: ${rawValue}")
            result += createStateEvents(rawValue)
        } else if (body?.property?.BinaryState?.text()) {
            def rawValue = body.property.BinaryState.text()
            debugLog("parse: Notify: BinaryState = ${rawValue}")
            result += createStateEvents(rawValue)
        } else if (body?.property?.TimeZoneNotification?.text()) {
            debugLog("parse: Notify: TimeZoneNotification = ${body.property.TimeZoneNotification.text()}")
        } else if (body?.Body?.GetBinaryStateResponse?.BinaryState?.text()) {
            def rawValue = body.Body.GetBinaryStateResponse.BinaryState.text()
            debugLog("parse: GetBinaryResponse: BinaryState = ${rawValue}")
            result += createStateEvents(rawValue)
        } else if (body?.Body?.GetInsightParamsResponse?.InsightParams?.text()) {
            def rawValue = body.Body.GetInsightParamsResponse.InsightParams.text()
            debugLog("parse: Got GetInsightParamsResponse: ${rawValue}")
            result += createStateEvents(rawValue)
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
    if (ipPort) {
        parent.childUpdatePort(device, ipPort);
    }
    if (ipAddress) {
        parent.childUpdateIp(device, ipAddress);
    }
    refresh()
}

private createBinaryStateEvent(rawValue) {
    // Insight switches actually support 3 values:
    //   0: off
    //   1: on
    //   8: standby
    // We consider 'standby' to be 'on'.
    debugLog("Creating binary state event for ${rawValue}")
    def value = rawValue == '0' ? 'off' : 'on';
    createEvent(
        name: 'switch',
        value: value,
        descriptionText: "Switch is ${value}"
    )
}

private createEnergyEvent(rawValue) {
    debugLog("Creating energy event for ${rawValue}")
    def value = (rawValue.toDouble() / 60000000).round(2)
    createEvent(
        name: 'energy',
        value: value,
        descriptionText: "Energy today is ${value} WH"
    )
}

private createPowerEvent(rawValue) {
    debugLog("Creating power event for ${rawValue}")
    def value = Math.round(rawValue.toInteger() / 1000)
    createEvent(
        name: 'power',
        value: value,
        descriptionText: "Power is ${value} W"
    )
}

// A state event will probably look like:
//   8|1536896687|5998|0|249789|1209600|118|190|164773|483265057
// Fields are:
//   8            on/off
//   1536896687   last changed at (UNIX timestamp)
//   5998         last on for (seconds?)
//   0            on today
//   249789       on total
//   1209600      window (seconds) over which onTotal is aggregated
//   110          average power (Watts)
//   190          current power (Watts?)
//   164773       energy today (mW mins)
//   483265057    energy total (mW mins)
private createStateEvents(stateString) {
    def params = stateString.split('\\|')
    debugLog("Event params: ${params}")
    def events = []
    if (params.size() > 0) {
        events << createBinaryStateEvent(params[0])
    }
    if (params.size() > 7) {
        events << createPowerEvent(params[7])
    }
    if (params.size() > 8) {
        events << createEnergyEvent(params[8])
    }
    events
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
