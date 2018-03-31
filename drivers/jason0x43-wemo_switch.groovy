/**
 * WeMo Switch driver
 *
 * Author: Jason Cheatham
 * Date: 2018-03-31
 *
 * Based on the original Wemo Switch driver by Juan Risso at SmartThings,
 * 2015-10-11.
 *
 * Copyright 2015 SmartThings
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
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

// parse events into attributes
def parse(description) {
	log.trace "Parsing '${description}'"

	def msg = parseLanMessage(description)
	def headerString = msg.header

	if (headerString?.contains('SID: uuid:')) {
		log.trace 'Header string: ' + headerString
		def sid = (headerString =~ /SID: uuid:.*/) ?
			(headerString =~ /SID: uuid:.*/)[0] :
			'0'
		sid -= 'SID: uuid:'.trim()
		log.trace 'Updating subscriptionId to ' + sid
		updateDataValue('subscriptionId', sid)
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
			log.trace "Notify: BinaryState = ${rawValue}"
			result << createBinaryStateEvent(rawValue)
		} else if (body?.property?.TimeZoneNotification?.text()) {
 			log.debug "Notify: TimeZoneNotification = ${body.property.TimeZoneNotification.text()}"
 		} else if (body?.Body?.GetBinaryStateResponse?.BinaryState?.text()) {
 			def rawValue = body.Body.GetBinaryStateResponse.BinaryState.text();
 			log.trace "GetBinaryResponse: BinaryState = ${rawValue}"
			result << createBinaryStateEvent(rawValue)
 		}
	}

	result
}

def on() {
	log.debug 'Executing on()'
	new hubitat.device.HubAction("""POST /upnp/control/basicevent1 HTTP/1.1
SOAPAction: "urn:Belkin:service:basicevent:1#SetBinaryState"
Host: ${getHostAddress()}
Content-Type: text/xml; charset="utf-8"
Content-Length: 333

<?xml version="1.0"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<SOAP-ENV:Body>
 <m:SetBinaryState xmlns:m="urn:Belkin:service:basicevent:1">
<BinaryState>1</BinaryState>
 </m:SetBinaryState>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>""", hubitat.device.Protocol.LAN)
}

def off() {
	log.debug 'Executing off()'
	new hubitat.device.HubAction("""POST /upnp/control/basicevent1 HTTP/1.1
SOAPAction: "urn:Belkin:service:basicevent:1#SetBinaryState"
Host: ${getHostAddress()}
Content-Type: text/xml; charset="utf-8"
Content-Length: 333

<?xml version="1.0"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<SOAP-ENV:Body>
 <m:SetBinaryState xmlns:m="urn:Belkin:service:basicevent:1">
<BinaryState>0</BinaryState>
 </m:SetBinaryState>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>""", hubitat.device.Protocol.LAN)
}

def refresh() {
 	log.debug 'Executing WeMo Switch subscribe, then timeSyncResponse, then poll'
	[subscribe(), timeSyncResponse(), poll()]
}

def subscribe() {
	def callback = getCallBackAddress()

	log.trace "Subscribing to address ${getHostAddress()}"
	log.trace "Callback to ${callback}"
	log.trace "Device MAC is ${device.deviceNetworkId}"

	new hubitat.device.HubAction("""SUBSCRIBE /upnp/event/basicevent1 HTTP/1.1
HOST: ${getHostAddress()}
CALLBACK: <http://${callback}>
NT: upnp:event
TIMEOUT: Second-${60 * (parent.interval?:5)}


""", hubitat.device.Protocol.LAN)
}

def unsubscribe() {
	log.debug 'Executing unsubscribe()'
	new hubitat.device.HubAction("""UNSUBSCRIBE /upnp/event/basicevent1 HTTP/1.1
HOST: ${getHostAddress()}
SID: uuid:${getDeviceDataByName('subscriptionId')}


""", hubitat.device.Protocol.LAN)
}

def resubscribe() {
	log.debug 'Executing "resubscribe()"'
	def sid = getDeviceDataByName('subscriptionId')
	new hubitat.device.HubAction("""SUBSCRIBE /upnp/event/basicevent1 HTTP/1.1
HOST: ${getHostAddress()}
SID: uuid:${sid}
TIMEOUT: Second-300


""", hubitat.device.Protocol.LAN)
}

def sync(ip, port) {
	log.trace "Syncing to ${ip}:${port}"
	def existingIp = getDataValue('ip')
	def existingPort = getDataValue('port')

	if (ip && ip != existingIp) {
		log.debug "Updating IP from ${existingIp} to ${ip}"
		updateDataValue('ip', ip)
	}

 	if (port && port != existingPort) {
 		log.debug "Updating port from $existingPort to $port"
 		updateDataValue('port', port)
	}

	subscribe()
}

//TODO: Use UTC Timezone
def timeSyncResponse() {
	log.debug 'Executing timeSyncResponse()'
	new hubitat.device.HubAction("""POST /upnp/control/timesync1 HTTP/1.1
SOAPACTION: "urn:Belkin:service:timesync:1#TimeSync"
HOST: ${getHostAddress()}
Content-Type: text/xml; charset="utf-8"
Content-Length: 376

<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
 <s:Body>
  <u:TimeSync xmlns:u="urn:Belkin:service:timesync:1">
   <UTC>${getTime()}</UTC>
   <TimeZone>-05.00</TimeZone>
   <dst>1</dst>
   <DstSupported>1</DstSupported>
  </u:TimeSync>
 </s:Body>
</s:Envelope>
""", hubitat.device.Protocol.LAN)
}

def setOffline() {
	sendEvent(
		name: 'switch',
		value: 'offline',
		descriptionText: 'The device is offline'
	)
}

def poll() {
	log.debug 'Executing poll()'
	if (device.currentValue('switch') != 'offline') {
		runIn(30, setOffline)
	}
	new hubitat.device.HubAction("""POST /upnp/control/basicevent1 HTTP/1.1
SOAPACTION: "urn:Belkin:service:basicevent:1#GetBinaryState"
HOST: ${getHostAddress()}
Content-Type: text/xml; charset="utf-8"
Content-Length: 277

<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body>
<u:GetBinaryState xmlns:u="urn:Belkin:service:basicevent:1">
</u:GetBinaryState>
</s:Body>
</s:Envelope>
""", hubitat.device.Protocol.LAN)
}

private getTime() {
	// This is essentially System.currentTimeMillis()/1000, but System is
	// disallowed by the sandbox.
	((new GregorianCalendar().time.time / 1000l).toInteger()).toString()
}

private getCallBackAddress() {
	def localIp = device.hub.getDataValue('localIP')
	def localPort = device.hub.getDataValue('localSrvPortTCP')
	"${localIp}:${localPort}"
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

 	// log.trace "Using ip ${ip} and port ${port} for device ${device.id}"
 	"${convertHexToIP(ip)}:${convertHexToInt(port)}"
}

private createBinaryStateEvent(rawValue) {
	def value = rawValue == '1' ? 'on' : 'off';
	createEvent(
		name: 'switch',
		value: value,
		descriptionText: "Switch is ${value}"
	)
}
