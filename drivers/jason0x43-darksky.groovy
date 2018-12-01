/**
 * DarkSky weather driver
 */

metadata {
    definition(name: 'DarkSky', namespace: 'jason0x43', author: 'Jason Cheatham') {
        capability('Sensor')
        capability('Temperature Measurement')
        capability('Illuminance Measurement')
        capability('Relative Humidity Measurement')
        capability('Refresh')

        attribute('temperatureHi', 'number')
        attribute('temperatureHiTime', 'number')
        attribute('temperatureLo', 'number')
        attribute('temperatureLoTime', 'number')
        attribute('temperature', 'number')
        attribute('precipChance', 'number')
        attribute('cloudCover', 'number')
    }
}

preferences {
    input(
        name: 'apiKey',
        type: 'text',
        title: 'Dark Sky API Key:',
        description: '',
        required: true
    )
    input(
        name: 'pollInterval',
        type: 'enum',
        title: 'Poll interval',
        defaultValue: '30 Minutes',
        options: ['10 Minutes', '30 Minutes', '1 Hour']
    )
}

def installed() {
    init()
}

def updated() {
    init()
}

def init() {
    unschedule()

    if (settings.apiKey) {
        def pollInterval = (settings.pollInterval ?: '30 Minutes').replace(' ', '')
        "runEvery${pollInterval}"(poll)
        log.debug "Scheduled poll for every ${pollInterval}"
        // poll()
        log.debug "${location.hubs[0]}"
    }
}

def poll() {
    try {
        log.debug "Requesting data..."
        def latitude = location.getLatitude()
        def longitude = location.getLongitude()
        httpGet(
            "https://api.darksky.net/forecast/${settings.apiKey}/${latitude},${longitude}"
        ) { resp -> 
            def data = resp.data
            def now = data.currently;
            sendEvent(name: 'temperature', value: now.temperature, unit: 'F')
            sendEvent(name: 'precipChance', value: now.precipProbability, unit: '%')
            sendEvent(name: 'cloudCover', value: now.cloudCover, unit: '%')

            def today = data.daily.data[0];
            sendEvent(name: 'temperatureHi', value: today.temperatureHigh, unit: 'F')
            sendEvent(name: 'temperatureHiTime', value: today.temperatureHighTime, unit: 's')
            sendEvent(name: 'temperatureLo', value: today.temperatureLow, unit: 'F')
            sendEvent(name: 'temperatureLoTime', value: today.temperatureLowTime, unit: 's')
        }
    } catch (e) {
        log.error "Error updating: ${e}"
    }
}

def refresh() {
    poll()
}
