/**
 * Circadian
 *
 * Author:  Jason Cheatham <j.cheatham@gmail.com>
 * Last updated: 2018-05-28, 11:14:28-0400
 *
 * Set light color temperature throughout the day.
 */

definition(
    name: 'Circadian',
    namespace: 'jason0x43',
    author: 'j.cheatham@gmail.com',
    singleInstance: true,
    description: 'Set light colors throughout the day.',
    iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
    iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png'
) 
 
preferences {
    page(name: 'mainPage')
}

def mainPage() {
    dynamicPage(name: 'mainPage', uninstall: true, install: true) {
        section('Select lights to manage:') {
            input(
                name: 'lights',
                type: 'capability.colorTemperature',
                multiple: true
            )
        }

        section('Options') {
            input(
                name: 'maxTemp',
                title: 'Maximum temperature, used at noon',
                type: 'number',
                defaultValue: 6500,
                required: true,
                range: '2200..6500'
            )
            input(
                name: 'minTemp',
                title: 'Minimum temperature, used at night',
                type: 'number',
                defaultValue: 2200,
                required: true,
                range: '2200..6500'
            )
            input(
                name: 'midTemp',
                title: 'Base temperature, used at dawn and dusk',
                type: 'number',
                defaultValue: 2700,
                required: true,
                range: '2200..6500'
            )
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def uninstalled() {
    log.debug 'Uninstalled'
    unsubscribe()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    subscribeToAll()
    updateColorTemp()
    runEvery5Minutes(updateColorTemp)
}

/**
 * Update the current color temp used for all lights
 */
def updateColorTemp() {
    log.debug 'Updating color temp'
    
    def times = getSunriseAndSunset(sunriseOffset: 0, sunsetOffset: 0)
    def sunrise = times.sunrise.time
    def sunset = times.sunset.time
    def midday = sunrise + ((sunset - sunrise) / 2)
    def now = new Date().time

    def maxTemp = settings.maxTemp
    def minTemp = settings.minTemp
    def tempRange = maxTemp - minTemp

    log.trace "sunrise: ${sunrise}, sunset: ${sunset}, midday: ${midday}"

    if (now > sunset && now < sunrise) {
        state.colorTemp = minTemp
    } else {
        def temp

        if (now > midday) {
            temp = maxTemp - ((now - midday) / (sunset - midday) * tempRange)
        } else {
            temp = minTemp + ((now - sunrise) / (midday - sunrise) * tempRange)
        }

        state.colorTemp = temp.toInteger()
    }

    log.debug "New colorTemp is ${state.colorTemp}"
    
    // Update the color temp of any lights that are currently on
    for (light in lights) {
        if (light.currentSwitch == 'on') {
            light.setColorTemperature(state.colorTemp)
        }
    }
}

/**
 * Update the color temp of a light when it's turned on
 */
def setLightTemp(evt) {
    def device = evt.getDevice()
    log.debug "Setting color temp for ${device} to ${state.colorTemp}"
    evt.getDevice().setColorTemperature(state.colorTemp)
}

private subscribeToAll() {
    for (light in lights) {
        subscribe(light, 'switch.on', setLightTemp)
        log.trace 'Subscribed to switch state for ' + light
    }
}
