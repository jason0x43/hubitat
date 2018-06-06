/**
 * WeMo Motion driver
 *
 * Author: Jason Cheatham
 * Last updated: 2018-06-05, 22:48:53-0400
 *
 * Based on the original Wemo Motion driver by SmartThings, 2013-10-11.
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
        name: 'Wemo Motion',
        namespace: 'jason0x43',
        author: 'SmartThings'
    ) {
        capability 'Motion Sensor'
        capability 'Refresh'
        capability 'Sensor'
        capability 'Polling'

        command 'subscribe'
        command 'unsubscribe'
        command 'resubscribe'
    }
}

def parse(description) {
    log.debug "Parsing '${description}'"

    def msg = parseLanMessage(description)
    def headerString = msg.header

    if (headerString?.contains('SID: uuid:')) {
        def sid = (headerString =~ /SID: uuid:.*/) ?
            (headerString =~ /SID: uuid:.*/)[0] :
            '0'
        sid -= 'SID: uuid:'.trim()
        log.trace 'Updating subscriptionId to ' + sid
        updateDataValue('subscriptionId', sid)

        def resubscribeTimeout = getSubscriptionTimeout() - 10
        log.trace "Scheduling resubscription in ${resubscribeTimeout} s"
        runIn(resubscribeTimeout, resubscribe)
    }

    def result = []
    def bodyString = msg.body
    if (bodyString) {
        try {
            unschedule('setOffline')
        } catch (e) {
            log.error 'unschedule(\'setOffline\')'
        }

        log.trace 'body: ' + bodyString
        def body = new XmlSlurper().parseText(bodyString)

        if (body?.property?.TimeSyncRequest?.text()) {
            log.trace 'Got TimeSyncRequest'
            result << timeSyncResponse()
        } else if (body?.Body?.SetBinaryStateResponse?.BinaryState?.text()) {
            def rawValue = body.Body.SetBinaryStateResponse.BinaryState.text()
            log.trace "Got SetBinaryStateResponse = ${rawValue}"
            result << createBinaryStateEvent(rawValue)
        } else if (body?.property?.BinaryState?.text()) {
            def rawValue = body.property.BinaryState.text()
            log.debug "Notify - BinaryState = ${rawValue}"
            result << createBinaryStateEvent(rawValue)
        } else if (body?.property?.TimeZoneNotification?.text()) {
            log.debug "Notify: TimeZoneNotification = ${body.property.TimeZoneNotification.text()}"
        } else if (body?.Body?.GetBinaryStateResponse?.BinaryState?.text()) {
            def rawValue = body.Body.GetBinaryStateResponse.BinaryState.text()
            log.trace "GetBinaryResponse: BinaryState = ${rawValue}"
            result << createBinaryStateEvent(rawValue)
        }
    }

    result
}

def poll() {
    log.debug 'Executing poll()'
    if (device.currentValue('motion') != 'offline') {
        runIn(30, setOffline)
    }

    new hubitat.device.HubSoapAction(
        path: '/upnp/control/basicevent1',
        urn: 'urn:Belkin:service:basicevent:1',
        action: 'GetBinaryState',
        headers: [
            HOST: getHostAddress()
        ]
    )
}

def refresh() {
    log.debug 'Executing subscribe(), then timeSyncResponse(), then poll()'
    [subscribe(), timeSyncResponse(), poll()]
}

def resubscribe() {
    log.debug 'Executing resubscribe()'
    new hubitat.device.HubAction(
        method: 'SUBSCRIBE',
        path: '/upnp/event/basicevent1',
        headers: [
            HOST: getHostAddress(),
            SID: "uuid:${getDeviceDataByName('subscriptionId')}",
            TIMEOUT: "Second-${getSubscriptionTimeout()}"
        ]
    )
}

def setOffline() {
    sendEvent(
        name: 'motion',
        value: 'offline',
        descriptionText: 'The device is offline'
    )
}

def subscribe() {
    log.debug 'Executing subscribe()'
    new hubitat.device.HubAction(
        method: 'SUBSCRIBE',
        path: '/upnp/event/basicevent1',
        headers: [
            HOST: getHostAddress(),
            CALLBACK: "<http://${getCallBackAddress()}/>",
            NT: 'upnp:event',
            TIMEOUT: "Second-${getSubscriptionTimeout()}"
        ]
    )
}

def sync(ip, port) {
    def existingIp = getDataValue('ip')
    def existingPort = getDataValue('port')

    if (ip && ip != existingIp) {
        log.debug "Updating ip from $existingIp to $ip"
        updateDataValue('ip', ip)
    }

    if (port && port != existingPort) {
        log.debug "Updating port from $existingPort to $port"
        updateDataValue('port', port)
    }

    subscribe()
}

def timeSyncResponse() {
    log.debug 'Executing timeSyncResponse()'
    new hubitat.device.HubSoapAction(
        path: '/upnp/control/timesync1',
        urn: 'urn:Belkin:service:timesync:1',
        action: 'TimeSync',
        body: [
            // TODO: Use UTC Timezone
            UTC: getTime(),
            TimeZone: '-05.00',
            dst: 1,
            DstSupported: 1
        ],
        headers: [
            HOST: getHostAddress()
        ]
    )
}

def unsubscribe() {
    log.debug 'Executing unsubscribe()'
    new hubitat.device.HubAction(
        method: 'UNSUBSCRIBE',
        path: '/upnp/event/basicevent1',
        headers: [
            HOST: getHostAddress(),
            SID: "uuid:${getDeviceDataByName('subscriptionId')}"
        ]
    )
}

def updated() {
    log.debug 'Updated'
    refresh()
}

private convertHexToInt(hex) {
     Integer.parseInt(hex,16)
}

private convertHexToIP(hex) {
     [
        convertHexToInt(hex[0..1]),
        convertHexToInt(hex[2..3]),
        convertHexToInt(hex[4..5]),
        convertHexToInt(hex[6..7])
    ].join('.')
}

private createBinaryStateEvent(rawValue) {
    def value = rawValue == '1' ? 'active' : 'inactive'
    createEvent(
        name: 'motion',
        value: value,
        descriptionText: "Motion is ${value}"
    )
}

private getCallBackAddress() {
    def localIp = device.hub.getDataValue('localIP')
    def localPort = device.hub.getDataValue('localSrvPortTCP')
    "${localIp}:${localPort}"
}

private getHostAddress() {
    def ip = getDataValue('ip')
    def port = getDataValue('port')

    if (!ip || !port) {
        def parts = device.deviceNetworkId.split(':')
        if (parts.length == 2) {
            ip = parts[0]
            port = parts[1]
        } else {
            log.warn "Can't figure out ip and port for device: ${device.id}"
        }
    }
    log.debug "Using ip: ${ip} and port: ${port} for device: ${device.id}"
    "${convertHexToIP(ip)}:${convertHexToInt(port)}"
}

private getTime() {
    // This is essentially System.currentTimeMillis()/1000, but System is
    // disallowed by the sandbox.
    ((new GregorianCalendar().time.time / 1000l).toInteger()).toString()
}

private getSubscriptionTimeout() {
    return 60 * (parent.interval?:5)
}
