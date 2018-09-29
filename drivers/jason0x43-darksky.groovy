/**
 * DarkSky weather driver
 */

metadata {
    definition(name: 'DarkSky', namespace: 'jason0x43', author: 'Jason Cheatham') {
        capability 'Notification'
        capability 'Sensor'
        capability 'Temperature Measurement'
        capability 'Illuminance Measurement'
        capability 'Relative Humidity Measurement'
    }
}

preferences {
    input(
        name: 'apiKey',
        type: 'text',
        title: 'API Key:',
        description: 'Dark Sky API Key ',
        required: true
    )
    input(
        name: 'pollInterval',
        type: 'enum',
        title: 'Poll interval',
        description: 'Poll interval in minutes',
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
        def pollInterval = (settings.pollInterval ?: '30 minutes').replace(' ', '')
        "runEvery${pollInterval}"(poll)
    }
}

def poll() {
    try {
        httpGet(
            uri
        ) { resp -> 
            log.debug "response: ${resp}"
        }
    } catch (e) {
        log.error "Error updating: ${e}"
    }
}
