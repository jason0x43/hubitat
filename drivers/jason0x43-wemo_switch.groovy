/**
 * WeMo Switch driver
 *
 * Author: Jason Cheatham
 * Last updated: 2019-06-09, 23:21:45-0400
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
        name: 'Wemo Switch',
        namespace: 'jason0x43',
        author: 'Jason Cheatham'
    ) {
        capability 'Actuator'
        capability 'Switch'
        capability 'Polling'
        capability 'Refresh'
        capability 'Sensor'

        command 'subscribe'
        command 'unsubscribe'
        command 'resubscribe'
        command 'updateAddress',
            [
              [name:"IP Address", type: "STRING", description: "New IP address", constraints: []]
            ]
        command 'updatePort', 
            [
              [name:"TCP Port", type: "STRING", description: "New TCP port", constraints: []]
            ]
    }
    }
}

def getDriverVersion() {
    2
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
            debugLog("parse: Got SetBinaryStateResponse = ${rawValue}")
            result << createBinaryStateEvent(rawValue)
        } else if (body?.property?.BinaryState?.text()) {
            def rawValue = body.property.BinaryState.text()
            debugLog("parse: Notify: BinaryState = ${rawValue}")
            result << createBinaryStateEvent(rawValue)
        } else if (body?.property?.TimeZoneNotification?.text()) {
            debugLog("parse: Notify: TimeZoneNotification = ${body.property.TimeZoneNotification.text()}")
        } else if (body?.Body?.GetBinaryStateResponse?.BinaryState?.text()) {
            def rawValue = body.Body.GetBinaryStateResponse.BinaryState.text()
            debugLog("parse: GetBinaryResponse: BinaryState = ${rawValue}")
            result << createBinaryStateEvent(rawValue)
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

private debugLog(message) {
    if (parent.debugLogging) {
        log.debug(message)
    }
}

private syncTime() {
    parent.childSyncTime(device)
}


def updatePort(port) {
    iPort=s2i(port)
    if (iPort < 1 || iPort > 65535 ) {
        infoLog("Invalid TCP port specified - ${port}")
        -1
    } else {
        hPort=i2h(iPort)
        def existingPort = device.getDataValue('port')

        infoLog("Existing Port: ${h2i(existingPort)}")
        
        if (iPort && iPort != h2i(existingPort)) {
            debugLog("Updating port from ${h2i(existingPort)} to ${port}")
            device.updateDataValue('port', hPort)
        }
        
    }
}

def updateAddress(ip) {
    
    String[] splitIp = ip.split("\\.")
    
    if (splitIp.length !=4) {
        infoLog("Invalid IP address specified - ${ip} (${splitIp.length} octets found)")
        -1
    } else {
        intArrIp=strArrToIntArr(splitIp)
        hexIp=intArrToHex(intArrIp)

        def existingIp = device.getDataValue('ip')

        infoLog("Existing IP: ${hexToIp(existingIp)}")
        
        if (ip && ip != hexToIp(existingIp)) {
            debugLog("Updating IP from ${hexToIp(existingIp)} to ${ip}")
            device.updateDataValue('ip', hexIp)
        }
    }
}

private strArrToIntArr(strArr) {
    int[] intArr = new int[strArr.length];
    for (int i = 0; i < strArr.length; i++) {
        intArr[i]=s2i(strArr[i])
    }
    intArr
}

private h2i(hex) {
    //Hex to Integer
    Integer.parseInt(hex,16)
}

private s2i(strV) {
    //String to Integer
    Integer.parseInt(strV, 10)
}

private hexToIp(hex) {
    //Hex IP String to dotted decimal IP string
    [
        h2i(hex[0..1]),
        h2i(hex[2..3]),
        h2i(hex[4..5]),
        h2i(hex[6..7])
    ].join('.')
}

private intArrToHex(arrAddress) {
    //integer array (split IP address) to Hex string
    hubitat.helper.HexUtils.intArrayToHexString(arrAddress)
}

private i2h(i) {
    //usage:  integerToHexString(value, minByteCount)
    hubitat.helper.HexUtils.integerToHexString(i, 1)
}

