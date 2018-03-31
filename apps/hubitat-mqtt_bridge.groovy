/**
 *  MQTT Bridge
 *
 *  Authors
 *   - st.john.johnson@gmail.com
 *   - jeremiah.wuenschel@gmail.com
 *
 *  Copyright 2016
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

definition(
	name: "MQTT Bridge",
	namespace: "hubitat",
	author: "St. John Johnson and Jeremiah Wuenschel and John Eubanks",
	description: "A bridge between Hubitat and MQTT",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@3x.png"
)

preferences {
	section("Send Notifications?") {
		input("recipients", "contact", title: "Send notifications to", multiple: true, required: false)
	}

	section ("Input") {
		CAPABILITY_MAP.each { key, capability ->
			input key, capability["capability"], title: capability["name"], multiple: true, required: false
		}
	}

	section ("Bridge") {
		input "bridge", "capability.notification", title: "Notify this Bridge", required: true, multiple: false
	}
}

/*
	def ROUTINES = location.helloHome?.getPhrases()*.label
	if (ROUTINES) {
		ROUTINES.sort()
	}

	def MODES = location.modes
	if (MODES) {
		MODES.sort()
	}
*/

// Massive lookup tree
@Field CAPABILITY_MAP = [
	"accelerationSensors": [
		name: "Acceleration Sensor",
		capability: "capability.accelerationSensor",
		attributes: [
			"acceleration"
		]
	],
	"actuator": [
		name: "Actuator",
		capability: "capability.actuator",
		attributes: [
		]
	],
	"airConditionerMode": [
		name: "Air Conditioner Mode",
		capability: "capability.airConditionerMode",
		attributes: [
			"airConditionerMode"
		],
		action: "actionAirConditionerMode"
	],
	"airQualitySensor": [
		name: "Air Quality Sensor",
		capability: "capability.airQualitySensor",
		attributes: [
			"airQuality"
		]
	],
	"alarm": [
		name: "Alarm",
		capability: "capability.alarm",
		attributes: [
			"alarm"
		],
		action: "actionAlarm"
	],
	"audioMute": [
		name: "Audio Mute",
		capability: "capability.audioMute",
		attributes: [
			"mute"
		],
		action: "actionAudioMute"
	],
	"audioNotification": [
		name: "Audio Notification",
		capability: "capability.audioNotification",
		attributes: [
		],
		action: "actionAudioNotification"
	],
	"audioTrackData": [
		name: "Audio Track Data",
		capability: "capability.audioTrackData",
		attributes: [
			"audioTrackData"
		]
	],
	"audioVolume": [
		name: "Audio Volume",
		capability: "capability.audioVolume",
		attributes: [
			"volume"
		],
		action: "actionAudioVolume"
	],
	"battery": [
		name: "Battery",
		capability: "capability.battery",
		attributes: [
			"battery"
		]
	],
	"beacon": [
		name: "Beacon",
		capability: "capability.beacon",
		attributes: [
			"presence"
		]
	],
	"bridge": [
		name: "bridge",
		capability: "capability.bridge",
		attributes: [
		]
	],
	"bulb": [
		name: "bulb",
		capability: "capability.bulb",
		attributes: [
			"switch"
		],
		action: "actionOnOff"
	],
	"pushablebutton": [
		name: "Pushable Button",
		capability: "capability.pushableButton",
		attributes: [
			"pushed",
			"numberOfButtons"
		]
	],
	"holdablebutton": [
		name: "Holdable Button",
		capability: "capability.holdableButton",
		attributes: [
			"held"
		]
	],
	"doubletapablebutton": [
		name: "DoubleTapable Button",
		capability: "capability.doubleTapableButton",
		attributes: [
			"doubleTapped"
		]
	],
	"carbonDioxideMeasurement": [
		name: "Carbon Dioxide Measurement",
		capability: "capability.carbonDioxideMeasurement",
		attributes: [
			"carbonDioxide"
		]
	],
	"carbonMonoxideDetector": [
		name: "Carbon Monoxide Detector",
		capability: "capability.carbonMonoxideDetector",
		attributes: [
			"carbonMonoxide"
		]
	],
	"colorControl": [
		name: "Color Control",
		capability: "capability.colorControl",
		attributes: [
			"color",
			"hue",
			"saturation"
		],
		action: "actionColorControl"
	],
	"colorTemperature": [
		name: "Color Temperature",
		capability: "capability.colorTemperature",
		attributes: [
			"colorTemperature"
		],
		action: "actionColorTemperature"
	],
	"configuration": [
		name: "Configuration",
		capability: "capability.configuration",
		attributes: [
		],
		action: "actionConfiguration"
	],
	"consumable": [
		name: "Consumable",
		capability: "capability.consumable",
		attributes: [
			"consumableStatus"
		],
		action: "actionConsumable"
	],
	"contactSensors": [
		name: "Contact Sensor",
		capability: "capability.contactSensor",
		attributes: [
			"contact"
		]
	],
	"dishwasherMode": [
		name: "Dishwasher Mode",
		capability: "capability.dishwasherMode",
		attributes: [
			"dishwasherMode"
		],
		action: "actionDishwasherMode"
	],
	"dishwasherOperatingState": [
		name: "Dishwasher Operating State",
		capability: "capability.dishwasherOperatingState",
		attributes: [
			"machineState",
			"supportedMachineStates",
			"dishwasherJobState",
			"remainingTime"
		],
		action: "actionDishwasherOperatingState"
	],
	"doorControl": [
		name: "Door Control",
		capability: "capability.doorControl",
		attributes: [
			"door"
		],
		action: "actionOpenClose"
	],
	"dryerMode": [
		name: "Dryer Mode",
		capability: "capability.dryerMode",
		attributes: [
			"dryerMode"
		],
		action: "actionDryerMode"
	],
	"dryerOperatingState": [
		name: "Dryer Operating State",
		capability: "capability.dryerOperatingState",
		attributes: [
			"machineState",
			"supportedMachineStates",
			"dryerJobState",
			"remainingTime"
		],
		action: "actionDryerOperatingState"
	],
	"dustSensor": [
		name: "Dust Sensor",
		capability: "capability.dustSensor",
		attributes: [
			"fineDustLevel",
			"dustLevel"
		]
	],
	"energyMeter": [
		name: "Energy Meter",
		capability: "capability.energyMeter",
		attributes: [
			"energy"
		]
	],
	"estimatedTimeOfArrival": [
		name: "Estimated Time Of Arrival",
		capability: "capability.estimatedTimeOfArrival",
		attributes: [
			"eta"
		]
	],
	"execute": [
		name: "Execute",
		capability: "capability.execute",
		attributes: [
			"data"
		],
		action: "actionExecute"
	],
	"fanSpeed": [
		name: "Fan Speed",
		capability: "capability.fanSpeed",
		attributes: [
			"fanSpeed"
		],
		action: "actionFanSpeed"
	],
	"filterStatus": [
		name: "Filter Status",
		capability: "capability.filterStatus",
		attributes: [
			"filterStatus"
		]
	],
	"garageDoors": [
		name: "Garage Door Control",
		capability: "capability.garageDoorControl",
		attributes: [
			"door"
		],
		action: "actionOpenClose"
	],
	"geolocation": [
		name: "Geolocation",
		capability: "capability.geolocation",
		attributes: [
			"latitude",
			"longitude",
			"method",
			"accuracy",
			"altitudeAccuracy",
			"heading",
			"speed",
			"lastUpdateTime"
		]
	],
	"holdableButton": [
		name: "Holdable Button",
		capability: "capability.holdableButton",
		attributes: [
			"button",
			"numberOfButtons"
		]
	],
	"illuminanceMeasurement": [
		name: "Illuminance Measurement",
		capability: "capability.illuminanceMeasurement",
		attributes: [
			"illuminance"
		]
	],
	"imageCapture": [
		name: "Image Capture",
		capability: "capability.imageCapture",
		attributes: [
			"image"
			],
		action: "actionImageCapture"
	],
	"indicator": [
		name: "Indicator",
		capability: "capability.indicator",
		attributes: [
			"indicatorStatus"
		],
		action: "actionIndicator"
	],
	"infraredLevel": [
		name: "Infrared Level",
		capability: "capability.infraredLevel",
		attributes: [
			"infraredLevel"
		],
		action: "actionInfraredLevel"
	],
	"light": [
		name: "Light",
		capability: "capability.light",
		attributes: [
			"switch"
		],
		action: "actionOnOff"
	],
	"lockOnly": [
		name: "Lock Only",
		capability: "capability.lockOnly",
		attributes: [
			"lock"
		],
		action: "actionLockOnly"
	],
	"lock": [
		name: "Lock",
		capability: "capability.lock",
		attributes: [
			"lock"
		],
		action: "actionLock"
	],
	"mediaController": [
		name: "Media Controller",
		capability: "capability.mediaController",
		attributes: [
			"activities",
			"currentActivity"
		],
		action: "actionMediaController"
	],
	"mediaInputSource": [
		name: "Media Input Source",
		capability: "capability.mediaInputSource",
		attributes: [
			"inputSource",
			"supportedInputSources"
		],
		action: "actionMediaInputSource"
	],
	"mediaPlaybackRepeat": [
		name: "Media Playback Repeat",
		capability: "capability.mediaPlaybackRepeat",
		attributes: [
			"playbackRepeatMode"
		],
		action: "actionMediaPlaybackRepeat"
	],
	"mediaPlaybackShuffle": [
		name: "Media Playback Shuffle",
		capability: "capability.mediaPlaybackShuffle",
		attributes: [
			"playbackShuffle"
		],
		action: "actionPlaybackShuffle"
	],
	"mediaPlayback": [
		name: "Media Playback",
		capability: "capability.mediaPlayback",
		attributes: [
			"level",
			"playbackStatus"
		],
		action: "actionMediaPlayback"
	],
	"mediaTrackControl": [
		name: "Media Track Control",
		capability: "capability.mediaTrackControl",
		attributes: [
		],
		action: "actionMediaTrackControl"
	],
	"momentary": [
		name: "Momentary",
		capability: "capability.momentary",
		attributes: [
		],
		action: "actionMomentary"
	],
	"motionSensors": [
		name: "Motion Sensor",
		capability: "capability.motionSensor",
		attributes: [
			"motion"
		],
		action: "actionActiveInactive"
	],
	"musicPlayer": [
		name: "Music Player",
		capability: "capability.musicPlayer",
		attributes: [
			"level",
			"mute",
			"status",
			"trackData",
			"trackDescription"
		],
		action: "actionMusicPlayer"
	],
	"notification": [
		name: "Notification",
		capability: "capability.notification",
		attributes: [
		],
		action: "actionNotification"
	],
	"odorSensor": [
		name: "Odor Sensor",
		capability: "capability.odorSensor",
		attributes: [
			"odorLevel"
		]
	],
	"outlet": [
		name: "Outlet",
		capability: "capability.outlet",
		attributes: [
			"switch"
		],
		action: "actionOnOff"
	],
	"ovenMode": [
		name: "Oven Mode",
		capability: "capability.ovenMode",
		attributes: [
			"ovenMode"
		],
		action: "actionOvenMode"
	],
	"ovenOperatingState": [
		name: "Oven Operating State",
		capability: "capability.ovenOperatingState",
		attributes: [
			"machineState",
			"supportedMachineStates",
			"ovenJobState",
			"remainingTime",
			"operationTime"
		],
		action: "actionOvenOperatingState"
	],
	"ovenSetpoint": [
		name: "Oven Setpoint",
		capability: "capability.ovenSetpoint",
		attributes: [
			"ovenSetpoint"
		],
		action: "actionOvenSetpoint"
	],
	"pHMeasurement": [
		name: "pH Measurement",
		capability: "capability.pHMeasurement",
		attributes: [
			"pH"
		]
	],
	"polling": [
		name: "Polling",
		capability: "capability.polling",
		attributes: [
		],
		action: "actionPolling"
	],
	"powerMeters": [
		name: "Power Meter",
		capability: "capability.powerMeter",
		attributes: [
			"power"
		]
	],
	"powerSource": [
		name: "Power Source",
		capability: "capability.powerSource",
		attributes: [
			"powerSource"
		]
	],
	"presenceSensors": [
		name: "Presence Sensor",
		capability: "capability.presenceSensor",
		attributes: [
			"presence"
		],
		action: "actionPresence"
	],
	"rapidCooling": [
		name: "Rapid Cooling",
		capability: "capability.rapidCooling",
		attributes: [
			"rapidCooling"
		],
		action: "actionRapidCooling"
	],
	"refresh": [
		name: "Refresh",
		capability: "capability.refresh",
		attributes: [
		],
		action: "actionRefresh"
	],
	"refrigerationSetpoint": [
		name: "Refrigeration Setpoint",
		capability: "capability.refrigerationSetpoint",
		attributes: [
			"refrigerationSetpoint"
		],
		action: "actionRefrigerationSetpoint"
	],
	"humiditySensors": [
		name: "Relative Humidity Measurement",
		capability: "capability.relativeHumidityMeasurement",
		attributes: [
			"humidity"
		]
	],
	"relaySwitch": [
		name: "Relay Switch",
		capability: "capability.relaySwitch",
		attributes: [
			"switch"
		],
		action: "actionOnOff"
	],
	"robotCleanerCleaningMode": [
		name: "Robot Cleaner Cleaning Mode",
		capability: "capability.robotCleanerCleaningMode",
		attributes: [
			"robotCleanerCleaningMode"
		],
		action: "actionRobotCleanerCleaningMode"
	],
	"robotCleanerMovement": [
		name: "Robot Cleaner Movement",
		capability: "capability.robotCleanerMovement",
		attributes: [
			"robotCleanerMovement"
		],
		action: "actionRobotCleanerMovement"
	],
	"robotCleanerTurboMode": [
		name: "Robot Cleaner Turbo Mode",
		capability: "capability.robotCleanerTurboMode",
		attributes: [
			"robotCleanerTurboMode"
		],
		action: "actionRobotCleanerTurboMode"
	],
	"sensor": [
		name: "Sensor",
		capability: "capability.sensor",
		attributes: [
		]
	],
	"shockSensor": [
		name: "Shock Sensor",
		capability: "capability.shockSensor",
		attributes: [
			"shock"
		]
	],
	"signalStrength": [
		name: "Signal Strength",
		capability: "capability.signalStrength",
		attributes: [
			"lqi",
			"rssi"
		]
	],
	"sleepSensor": [
		name: "Sleep Sensor",
		capability: "capability.sleepSensor",
		attributes: [
			"sleeping"
		]
	],
	"smokeDetector": [
		name: "Smoke Detector",
		capability: "capability.smokeDetector",
		attributes: [
			"smoke"
		]
	],
	"soundPressureLevel": [
		name: "Sound Pressure Level",
		capability: "capability.soundPressureLevel",
		attributes: [
			"soundPressureLevel"
		]
	],
	"soundSensor": [
		name: "Sound Sensor",
		capability: "capability.soundSensor",
		attributes: [
			"sound"
		]
	],
	"speechRecognition": [
		name: "Speech Recognition",
		capability: "capability.speechRecognition",
		attributes: [
			"phraseSpoken"
		]
	],
	"speechSynthesis": [
		name: "Speech Synthesis",
		capability: "capability.speechSynthesis",
		attributes: [
		],
		action: "actionSpeechSynthesis"
	],
	"stepSensor": [
		name: "Step Sensor",
		capability: "capability.stepSensor",
		attributes: [
			"goal",
			"steps"
		]
	],
	"switchLevel": [
		name: "Switch Level",
		capability: "capability.switchLevel",
		attributes: [
			"level"
		],
		action: "actionSwitchLevel"
	],
	"switches": [
		name: "Switch",
		capability: "capability.switch",
		attributes: [
			"switch"
		],
		action: "actionOnOff"
	],
	"tamperAlert": [
		name: "Tamper Alert",
		capability: "capability.tamperAlert",
		attributes: [
			"tamper"
		]
	],
	"temperatureSensors": [
		name: "Temperature Measurement",
		capability: "capability.temperatureMeasurement",
		attributes: [
			"temperature"
		]
	],
	"thermostatCoolingSetpoint": [
		name: "Thermostat Cooling Setpoint",
		capability: "capability.thermostatCoolingSetpoint",
		attributes: [
			"coolingSetpoint",
			"coolingSetpointRange"
		],
		action: "actionThermostatCoolingSetpoint"
	],
	"thermostatFanMode": [
		name: "Thermostat Fan Mode",
		capability: "capability.thermostatFanMode",
		attributes: [
			"thermostatFanMode",
			"supportedThermostatFanModes"
		],
		action: "actionThermostatFanMode"
	],
	"thermostatHeatingSetpoint": [
		name: "Thermostat Heating Setpoint",
		capability: "capability.thermostatHeatingSetpoint",
		attributes: [
			"heatingSetpoint",
			"heatingSetpointRange"
		],
		action: "actionThermostatHeatingSetpoint"
	],
	"thermostatMode": [
		name: "Thermostat Mode",
		capability: "capability.thermostatMode",
		attributes: [
			"thermostatMode",
			"supportedThermostatModes"
		],
		action: "actionThermostatMode"
	],
	"thermostatOperatingState": [
		name: "Thermostat Operating State",
		capability: "capability.thermostatOperatingState",
		attributes: [
			"thermostatOperatingState"
		]
	],
	"thermostatSetpoint": [
		name: "Thermostat Setpoint",
		capability: "capability.thermostatSetpoint",
		attributes: [
			"thermostatSetpoint",
			"thermostatSetpointRange"
		]
	],
	"thermostat": [
		name: "Thermostat",
		capability: "capability.thermostat",
		attributes: [
			"coolingSetpoint",
			"coolingSetpointRange",
			"heatingSetpoint",
			"heatingSetpointRange",
			"schedule",
			"temperature",
			"thermostatFanMode",
			"supportedThermostatFanModes",
			"thermostatMode",
			"supportedThermostatModes",
			"thermostatOperatingState",
			"thermostatSetpoint",
			"thermostatSetpointRange"
		],
		action: "actionThermostat"
	],
	"threeAxis": [
		name: "Three Axis",
		capability: "capability.threeAxis",
		attributes: [
			"threeAxis"
		]
	],
	"timedSession": [
		name: "Timed Session",
		capability: "capability.timedSession",
		attributes: [
			"sessionStatus",
			"timeRemaining"
		],
		action: "actionTimedSession"
	],
	"tone": [
		name: "Tone",
		capability: "capability.tone",
		attributes: [
		],
		action: "actionTone"
	],
	"touchSensor": [
		name: "Touch Sensor",
		capability: "capability.touchSensor",
		attributes: [
			"touch"
		]
	],
	"tVChannel": [
		name: "TV Channel",
		capability: "capability.tVChannel",
		attributes: [
			"tvChannel"
		],
		action: "actionTvChannel"
	],
	"ultravioletIndex": [
		name: "Ultraviolet Index",
		capability: "capability.ultravioletIndex",
		attributes: [
			"ultravioletIndex"
		]
	],
	"valve": [
		name: "Valve",
		capability: "capability.valve",
		attributes: [
			"contact",
			"valve"
		],
		action: "actionOpenClose"
	],
	"videoStream": [
		name: "Video Stream",
		capability: "capability.videoStream",
		attributes: [
			"stream"
		],
		action: "actionVideoStream"
	],
	"voltageMeasurement": [
		name: "Voltage Measurement",
		capability: "capability.voltageMeasurement",
		attributes: [
			"voltage"
		]
	],
	"washerMode": [
		name: "Washer Mode",
		capability: "capability.washerMode",
		attributes: [
			"ovenMode"
		],
		action: "actionWasherMode"
	],
	"washerOperatingState": [
		name: "Washer Operating State",
		capability: "capability.washerOperatingState",
		attributes: [
			"machineState",
			"supportedMachineStates",
			"washerJobState",
			"remainingTime"
		],
		action: "actionWasherOperatingState"
	],
	"waterSensors": [
		name: "Water Sensor",
		capability: "capability.waterSensor",
		attributes: [
			"water"
		]
	],
	"windowShades": [
		name: "Window Shade",
		capability: "capability.windowShade",
		attributes: [
			"windowShade"
		],
		action: "actionWindowShade"
	]
]

def installed() {
	log.debug "Installed with settings: ${settings}"

	runEvery15Minutes(initialize)
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	// Unsubscribe from all events
	unsubscribe()

	// Subscribe to stuff
	initialize()
}

// Return list of displayNames
def getDeviceNames(devices) {
	def list = []
	devices.each{device->
		list.push(device.displayName)
	}
	list
}

def initialize() {
	// subscribe to mode/routine changes
	subscribe(location, "mode", inputHandler)
	subscribe(location, "routineExecuted", inputHandler)

	// Subscribe to new events from devices
	CAPABILITY_MAP.each { key, capability ->
		capability["attributes"].each { attribute ->
			subscribe(settings[key], attribute, inputHandler)
		}
	}

	// Subscribe to events from the bridge
	subscribe(bridge, "message", bridgeHandler)

	// Update the bridge
	updateSubscription()
}

// Update the bridge's subscription
def updateSubscription() {
	def attributes = [
		notify: ["Contacts", "System"]
	]

	CAPABILITY_MAP.each { key, capability ->
		capability["attributes"].each { attribute ->
			if (!attributes.containsKey(attribute)) {
				attributes[attribute] = []
			}
			settings[key].each {device ->
				attributes[attribute].push(device.displayName)
			}
		}
	}

	def json = new groovy.json.JsonOutput().toJson([
		path: "/subscribe",
		body: [
			devices: attributes
		]
	])

	log.debug "Updating subscription: ${json}"

	bridge.deviceNotification(json)
}

// Receive an event from the bridge
def bridgeHandler(evt) {
	def json = new JsonSlurper().parseText(evt.value)
	log.debug "Received device event from bridge: ${json}"

	if (json.type == "notify") {
		if (json.name == "Contacts") {
			sendNotificationToContacts("${json.value}", recipients)
		} else {
			sendNotificationEvent("${json.value}")
		}
		return
	} else if (json.type == "modes") {
		actionModes(json.value)
		return
	} else if (json.type == "routines") {
		actionRoutines(json.value)
		return
	}

	// @NOTE this is stored AWFUL, we need a faster lookup table
	// @NOTE this also has no fast fail, I need to look into how to do that
	CAPABILITY_MAP.each { key, capability ->
		if (capability["attributes"].contains(json.type)) {
			settings[key].each {device ->
				if (device.displayName == json.name) {

					if (json.command == false) {
						if (device.getSupportedCommands().any {it.name == "setStatus"}) {
							log.debug "Setting state ${json.type} = ${json.value}"
							device.setStatus(json.type, json.value)
							state.ignoreEvent = json;
						}
					} else {
						if (capability.containsKey("action")) {
							def action = capability["action"]
							// Yes, this is calling the method dynamically
							"$action"(device, json.type, json.value)
						}
					}

				}
			}
		}
	}
}

// Receive an event from a device
def inputHandler(evt) {
	if (state.ignoreEvent
		&& state.ignoreEvent.name == evt.displayName
		&& state.ignoreEvent.type == evt.name
		&& state.ignoreEvent.value == evt.value
	) {
		log.debug "Ignoring event ${state.ignoreEvent}"
		state.ignoreEvent = false;
	}
	else {
		def json = new JsonOutput().toJson([
			path: "/push",
			body: [
				name: evt.displayName,
				value: evt.value,
				type: evt.name
			]
		])

		log.debug "Forwarding device event to bridge: ${json}"
		bridge.deviceNotification(json)
	}
}

/*
 * ACTIONS
 */

// +---------------------------------+
// | WARNING, BEYOND HERE BE DRAGONS |
// +---------------------------------+
// These are the functions that handle incoming messages from MQTT.
// I tried to put them in closures but apparently SmartThings Groovy sandbox
// restricts you from running closures from an object (it's not safe).
// --
// John E - Note there isn't the same sandbox for Hubitat.  So head
// the original warning.

def actionAirConditionerMode(device, attribute, value) {
	device.setAirConditionerMode(value)
}

def actionAlarm(device, attribute, value) {
	switch (value) {
		case "both":
			device.both()
			break
		case "off":
			device.off()
			break
		case "siren":
			device.siren()
			break
		case "strobe":
			device.strobe()
			break
	}
}

def actionAudioMute(device, attribute, value) {
	device.setMute(value)
}

def actionAudioNotification(device, attribute, value) {
//value0: URI of the track to be played
//value1: volume level
	switch (attribute) {
		case "playTrack":
			def values = value.split(',')
			device.playTrack(values[0], values[1])
			break
		case "playTrackAndResume":
			def values = value.split(',')
			device.playTrackAndResume(values[0], values[1])
			break
		case "playTrackAndRestore":
			def values = value.split(',')
			device.playTrackAndRestore(values[0], values[1])
			break
	}
}

def actionAudioVolume(device, attribute, value) {
	switch (attribute) {
		case "setVolume":
			device.setVolume(value)
			break
		case "volumeUp":
			device.volumeUp()
			break
		case "volumeDown":
			device.volumeDown()
			break
	}
}

def actionColorControl(device, attribute, value) {
	switch (attribute) {
		case "setColor":
			def values = value.split(',')
			def colormap = ["hue": values[0] as int, "saturation": values[1] as int]
			device.setColor(colormap)
			break
		case "setHue":
			device.setHue(value as int)
			break
		case "setSaturation":
			device.setSaturation(value as int)
			break
	}
}

def actionColorTemperature(device, attribute, value) {
	device.setColorTemperature(value as int)
}

def actionPresence(device, attribute, value) {
	if (value == "present") {
		device.arrived();
	} else if (value == "not present") {
		device.departed();
	}
}

def actionConfiguration(device, attribute, value) {
//	device.configure()
}

def actionConsumable(device, attribute, value) {
	device.setConsumableStatus(value)
}

def actionDishwasherMode(device, attribute, value) {
	device.setDishwasherMode(value)
}

def actionDishwasherOperatingState(device, attribute, value) {
	device.setMachineState(value)
}

def actionDryerMode(device, attribute, value) {
	device.setDryerMode(value)
}

def actionDryerOperatingState(device, attribute, value) {
	device.setMachineState(value)
}

def actionExecute(device, attribute, value) {
	device.execute(attribute, value)
}

def actionFanSpeed(device, attribute, value) {
	device.setFanSpeed(value)
}

def actionImageCapture(device, attribute, value) {
	device.take()
}

def actionIndicator(device, attribute, value) {
	switch (value) {
		case "indicatorNever":
			device.indicatorNever()
			break
		case "indicatorWhenOff":
			device.indicatorWhenOff()
			break
		case "indicatorWhenOn":
			device.indicatorWhenOn()
			break
	}
}

def actionInfraredLevel(device, attribute, value) {
	device.setInfraredLevel(value)
}

def actionLockOnly(device, attribute, value) {
	device.lock()
}

def actionLock(device, attribute, value) {
	if (value == "lock") {
		device.lock()
	} else if (value == "unlock") {
		device.unlock()
	}
}

def actionMediaController(device, attribute, value) {
	device.startActivity(value)
}

def actionMediaInputSource(device, attribute, value) {
	device.setInputSource(value)
}

def actionMediaPlaybackRepeat(device, attribute, value) {
	device.setPlaybackRepeatMode(value)
}

def actionPlaybackShuffle(device, attribute, value) {
	device.setPlaybackShuffle(value)
}

def actionMediaPlayback(device, attribute, value) {
	switch(attribute) {
		case "setPlaybackStatus":
			device.setPlaybackStatus(value)
			break
		case "play":
			device.play()
			break
		case "pause":
			device.pause()
			break
		case "stop":
			device.stop()
			break
	}
}

def actionMediaTrackControl(device, attribute, value) {
	if (value == "nextTrack") {
		device.nextTrack()
	} else if (value == "previousTrack") {
		device.previousTrack()
	}
}

def actionMomentary(device, attribute, value) {
	device.push()
}

def actionMusicPlayer(device, attribute, value) {
	switch(attribute) {
		case "mute":
			device.mute()
			break
		case "nextTrack":
			device.nextTrack()
			break
		case "pause":
			device.pause()
			break
		case "play":
			device.play()
			break
		case "playTrack":
			device.playTrack(value)
			break
		case "previousTrack":
			device.previousTrack()
			break
		case "restoreTrack":
			device.restoreTrack(value)
			break
		case "resumeTrack":
			device.resumeTrack(value)
			break
		case "setLevel":
			device.setLevel(value)
			break
		case "setTrack":
			device.setTrack(value)
			break
		case "stop":
			device.stop()
			break
		case "unmute":
			device.unmute()
			break
/*		case "status":
			if (device.getSupportedCommands().any {it.name == "setStatus"}) {
				device.setStatus(value)
			}
			break*/
	}
}

def actionNotification(device, attribute, value) {
	device.deviceNotification(value)
}

def actionOvenMode(device, attribute, value) {
	device.setOvenMode(value)
}

def actionOvenOperatingState(device, attribute, value) {
	switch (attribute) {
		case "setMachineState":
			device.setMachineState(value)
			break
		case "stop":
			device.stop()
			break
	}
}

def actionOvenSetpoint(device, attribute, value) {
	device.setOvenSetpoint(value)
}

def actionPolling(device, attribute, value) {
	device.poll()
}

def actionRapidCooling(device, attribute, value) {
	device.setRapidCooling(value)
}

def actionRefrigerationSetpoint(device, attribute, value) {
	device.setRefrigerationSetpoint(value)
}

def actionRobotCleanerCleaningMode(device, attribute, value) {
	device.setRobotCleanerCleaningMode(value)
}

def actionRobotCleanerMovement(device, attribute, value) {
	device.setRobotCleanerMovement(value)
}

def actionRobotCleanerTurboMode(device, attribute, value) {
	device.setRobotCleanerTurboMode(value)
}

def actionSpeechSynthesis(device, attribute, value) {
	device.speak(value)
}

def actionSwitchLevel(device, attribute, value) {
	device.setLevel(value as int)
}

def actionThermostatCoolingSetpoint(device, attribute, value) {
	device.setCoolingSetpoint(value)
}

def actionThermostatFanMode(device, attribute, value) {
	device.setThermostatFanMode(value)
}

def actionThermostatHeatingSetpoint(device, attribute, value) {
	device.setHeatingSetpoint(value)
}

def actionThermostatMode(device, attribute, value) {
	device.setThermostatMode(value)
}

def actionThermostat(device, attribute, value) {
	switch (attribute) {
		case "auto":
			device.auto()
			break
		case "cool":
			device.cool()
			break
		case "emergencyHeat":
			device.emergencyHeat()
			break
		case "fanAuto":
			device.fanAuto()
			break
		case "fanCirculate":
			device.fanCirculate()
			break
		case "fanOn":
			device.fanOn()
			break
		case "heat":
			device.heat()
			break
		case "off":
			device.off()
			break
		case "setCoolingSetpoint":
			device.setCoolingSetpoint(value)
			break
		case "setHeatingSetpoint":
			device.setHeatingSetpoint(value)
			break
			device.setSchedule(value)
		case "setThermostatFanMode":
			device.setThermostatFanMode(value)
			break
		case "setThermostatMode":
			device.setThermostatMode(value)
			break
	}
}

def actionTimedSession(device, attribute, value) {
	switch (attribute) {
		case "cancel":
			device.cancel()
			break
		case "pause":
			device.pause()
			break
		case "setTimeRemaining":
			device.setTimeRemaining(value)
			break
		case "start":
			device.start()
			break
		case "stop":
			device.stop()
			break
	}
}

def actionTone(device, attribute, value) {
	device.beep()
}

def actionTvChannel(device, attribute, value) {
	switch (attribute) {
		case "setTvChannel":
			device.setTvChannel(value)
			break
		case "channelUp":
			device.channelUp()
			break
		case "channelDown":
			device.channelDown()
			break
	}
}

def actionVideoStream(device, attribute, value) {
	if (value == "startStream") {
		device.startStream()
	} else if (value == "stopStream") {
		device.stopStream()
	}
}

def actionWasherMode(device, attribute, value) {
	device.setWasherMode(value)
}

def actionWasherOperatingState(device, attribute, value) {
	device.setMachineState(value)
}

def actionWindowShade(device, attribute, value) {
	switch (attribute) {
		case "close":
			device.close(value)
			break
		case "open":
			device.open()
			break
		case "presetPosition":
			device.presetPosition()
			break
	}
}

/*
 * Generic Actions
 * Routines & Modes Actions
 */

def actionOpenClose(device, attribute, value) {
	if (value == "open") {
		device.open()
	} else if (value == "close") {
		device.close()
	}
}

def actionOnOff(device, attribute, value) {
	if (value == "off") {
		device.off()
	} else if (value == "on") {
		device.on()
	}
}

def actionRoutines(value) {
	location.helloHome?.execute(value)
}

def actionModes(value) {
	if (location.mode != value) {
		if (location.modes?.find{it.name == value}) {
			location.setMode(value)
		} else {
			log.warn "MQTT_Bridge: unknown mode ${value}"
		}
	}
}