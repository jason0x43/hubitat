/**
 * WeMo Switch driver
 *
 * Author: Jason Cheatham
 * Last updated: 2018-07-15, 22:29:06-0400
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
    }
}

def on() {
    log.debug 'on()'
    parent.childSetBinaryState(device, '1')
}

def off() {
    log.debug 'off()'
    parent.childSetBinaryState(device, '0')
}

def parse(description) {
    log.trace 'parse()'

    def msg = parseLanMessage(description)

    def subscriptionData = parent.getSubscriptionData(msg)
    if (subscriptionData != null) {
        log.trace "Updating subscriptionId to ${subscriptionData.sid}"
        updateDataValue('subscriptionId', subscriptionData.sid)
        log.trace "Scheduling resubscription in ${subscriptionData.timeout} s"
        runIn(subscriptionData.timeout, resubscribe)
    }

    def result = []
    def bodyString = msg.body
    if (bodyString) {
        try {
            unschedule('setOffline')
        } catch (e) {
            log.error 'unschedule("setOffline")'
        }

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
            log.trace "Notify: BinaryState = ${rawValue}"
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
    log.debug 'poll()'
    if (device.currentValue('switch') != 'offline') {
        runIn(30, setOffline)
    }
    parent.childGetBinaryState(device)
}

def refresh() {
    log.debug 'refresh()'
    parent.childRefresh(device)
}

def resubscribe() {
    log.debug 'resubscribe()'
    runIn(10, subscribeIfNecessary)
    parent.childResubscribe(device)
}

def scheduleResubscribe(timeout) {
    runIn(resubscribeTimeout, resubscribe)
}

def setOffline() {
    sendEvent(
        name: 'switch',
        value: 'offline',
        descriptionText: 'The device is offline'
    )
}

def subscribe() {
    log.debug 'subscribe()'
    parent.childSubscribe(device)
}

def subscribeIfNecessary() {
    log.trace 'subscribeIfNecessary'
    parent.childSubscribeIfNecessary(device)
}

def sync(ip, port) {
    log.debug 'sync()'
    parent.childSync(device, ip, port)
}

def timeSyncResponse() {
    log.debug 'Executing timeSyncResponse()'
    parent.childTimeSyncResponse(device)
}

def unsubscribe() {
    log.debug 'unsubscribe()'
    parent.childUnsubscribe(device)
}

def updated() {
    log.debug 'Updated'
    refresh()
}

private createBinaryStateEvent(rawValue) {
    def value = rawValue == '0' ? 'off' : 'on'
    createEvent(
        name: 'switch',
        value: value,
        descriptionText: "Switch is ${value}"
    )
}
