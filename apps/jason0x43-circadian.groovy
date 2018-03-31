/**
 * Circadian
 *
 * Author:  Jason Cheatham <j.cheatham@gmail.com>
 * Date:    2018-03-27
 * Version: 1.0
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
        section('Select lights to manage...') {
           input 'lights', 'capability.colorTemperature', multiple: true
           // input 'frequency', 'number', title: 'Select update frequency (default is 5 minutes)'
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
    
    // Update color temp based on current time
    def times = getSunriseAndSunset(sunriseOffset: -60, sunsetOffset: -60)
    def now = new Date()
    def sunrise = times.sunrise
    def sunset = times.sunset

    if (now.after(sunset) && now.before(sunrise)) {
        // At night it's always 2200
        state.colorTemp = 2200;
    } else {
        // During the day the temp starts out at 3200 and gradually moves
        // down to 2200
        def riseTime = sunrise.getTime()
        def setTime = sunset.getTime()
        def range = setTime - riseTime
        def nowTime = now.getTime()
        // minutes from sunrise to now
        def delta = nowTime - riseTime
        //log.trace "Delta: ${delta}"
        // percent progress
        def progress = delta / range;
        //log.trace "Progress: ${progress}"

        state.colorTemp = (3200 - progress * 1000).toInteger()
        log.debug "New colorTemp is ${state.colorTemp}"
    }
    
    // Update the color temp of any lights that are curr ntly on
    for (light in lights) {
        if (light.latestValue('switch').toString() == 'on') {
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