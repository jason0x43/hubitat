/**
 * Scene
 *
 * Author:  Jason Cheatham <j.cheatham@gmail.com>
 * Last updated: 2018-09-28, 23:16:06-0400
 * Version: 1.0
 *
 * Based on Scene Machine by Todd Wackford
 * https://github.com/twack/smarthings-apps/blob/master/scene-machine.app.groovy
 *
 * This is a customized version of Scene Machine that automatically creates a
 * switch to trigger the scene and that can handle color temperature as well as
 * light level.
 *
 * Use License: Non-Profit Open Software License version 3.0 (NPOSL-3.0)
 *              http://opensource.org/licenses/NPOSL-3.0
 */

definition(
    name: 'Scene',
    namespace: 'jason0x43',
    parent: 'jason0x43:Scene Manager',
    author: 'j.cheatham@gmail.com',
    description: 'Create a scene from a set of switches.',
    iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
    iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png'
) 
 
preferences {
    page(name: 'mainPage')
}

def mainPage() {
    dynamicPage(name: 'mainPage', uninstall: true, install: true) {
        section {
            paragraph(
                'Create a scene by selecting groups of lights and choosing ' +
                'how they should be configured.'
            )

            paragraph(
                'When a group is turned "on", the color temp OR hue+' +
                'sat may be set, along with the level. If both the color ' +
                'temp and hue+sat are configured, color temp will take priority.'
            )
            label(title: 'Scene name', type: 'string', required: true)
        }

        getLightGroups().each {
            def name = it
            def id = name.substring(10)

            // Need to use sprintf when generating property names so they'll be
            // regular strings
            def state = sprintf('sceneSwitch%s', id)
            def level = sprintf('sceneLevel%s', id)
            def colorTemperature = sprintf('sceneColorTemperature%s', id)
            def hue = sprintf('sceneHue%s', id)
            def saturation = sprintf('sceneSaturation%s', id)

            section('Lights') {
                input(
                    name: name,
                    type: 'capability.switch',
                    multiple: true,
                    submitOnChange: true
                )

                input(
                    name: state,
                    title: 'On/off',
                    type: 'bool',
                    submitOnChange: true
                )

                if (settings[state]) {
                    if (supportsLevel(name)) {
                        input(name: level, title: 'Level', type: 'number')
                    }

                    if (supportsColorTemp(name)) {
                        input(name: colorTemperature, title: 'Color temperature', type: 'number')
                    }

                    if (supportsColor(name)) {
                        input(name: hue, title: 'Color hue', type: 'number', range: '0..100')
                            input(name: saturation, title: 'Color saturation', type: 'number', range: '0..100')
                    }
                }
            }
        }

        def name = sprintf('sceneLight%d', getNextId())

        section('Lights') {
            input(name: name, type: 'capability.switch', multiple: true, submitOnChange: true)
        }

        section('Button') {
            paragraph('Optionally choose a button to push when activating the scene')
            input(name: 'sceneButton', type: 'capability.pushableButton', submitOnChange: true)

            if (settings.sceneButton != null) {
                def numButtons = sceneButton.currentNumberOfButtons
                if (numButtons != null) {
                    def options = (1..numButtons).collect { it.toString() }
                    input(
                        name: 'sceneButtonIndex',
                        title: 'Which button? (No selection means any button)',
                        type: 'enum',
                        options: options
                    )
                } else {
                    paragraph(
                        "This device didn't report a number of buttons. You can " +
                        'manually enter a numeric index here. If you provide a ' +
                        'value, the scene will push that button when activated. If ' +
                        'not, it will push all the device buttons.'
                    )
                    input(
                        name: 'sceneButtonIndex',
                        title: 'Which button?',
                        type: 'number'
                    )
                }
            }
        }

        section('Alternate triggers') {
            paragraph(
                'Optionally choose a button and/or momentary switch that can ' +
                'trigger the scene in addition to the default virtual switch.'
            )
            input(
                name: 'triggerSwitch',
                title: 'Switch',
                type: 'capability.switch',
                submitOnChange: true
            )
            input(
                name: 'triggerButton',
                title: 'Button device',
                type: 'capability.pushableButton',
                submitOnChange: true
            )

            if (settings.triggerButton != null) {
                def numButtons = triggerButton.currentNumberOfButtons
                if (numButtons != null) {
                    def options = (1..numButtons).collect { it.toString() }
                    input(
                        name: 'triggerButtonIndex',
                        title: 'Which button? (No selection means any button)',
                        type: 'enum',
                        options: options
                    )
                } else {
                    paragraph(
                        "This switch didn't report a number of buttons. You can " +
                        'manually enter a numeric index here. If you provide a ' +
                        'value, the scene will subscribe to that button. If ' +
                        'not, it will respond to any button press.'
                    )
                    input(
                        name: 'triggerButtonIndex',
                        title: 'Which button?',
                        type: 'number'
                    )
                }
            }
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    init()
}

def uninstalled() {
    log.debug "Uninstalled with settings: ${settings}"

    def childId = getDeviceID()
    unsubscribe()
    deleteChildDevice(childId)
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    init()
}

def init() {
    log.debug "Initting..."

    // Create a switch to activate the scene
    def child = createChildDevice(app.label)

    subscribe(child, 'switch.on', setScene)
    log.trace "Subscribed to scene switch"

    if (triggerButton) {
        if (triggerButtonIndex != null) {
            subscribe(triggerButton, "pushed.${triggerButtonIndex}", setScene)
            log.trace "Subscribed to button ${triggerButtonIndex}"
        } else {
            subscribe(triggerButton, 'pushed', setScene)
            log.trace "Subscribed to all buttons"
        }
    }

    if (triggerSwitch) {
        subscribe(triggerSwitch, 'switch.on', setScene)
        log.trace "Subscribed to switch"
    }
}

def setScene(evt) {
    log.debug 'Setting scene'
    
    getLightGroups().each {
        def name = it
        def state = getLightState(name)
        def group = settings[name]

        group.each {
            def light = it
            def currentSwitch = light.currentSwitch

            log.trace "state for ${light}: ${state}"

            if (state.switch) {
                if (currentSwitch != 'on') {
                    light.on()
                }

                if (state.level != null && state.hue != null && state.saturation != null) {
                    light.setColor(hue: state.hue, level: state.level, saturation: state.saturation)
                }
                if (state.level != null) {
                    light.setLevel(state.level)
                }

                if (state.hue != null) {
                    light.setHue(state.hue)
                }

                if (state.saturation != null) {
                    light.setSaturation(state.saturation)
                }

                if (state.colorTemperature != null) {
                    light.setColorTemperature(state.colorTemperature)
                }
            } else if (currentSwitch == 'on') {
                light.off()
            }
        }
    }

    if (settings.sceneButton != null) {
        if (settings.sceneButtonIndex != null) {
            sceneButton.push(sceneButtonIndex)
        } else {
            sceneButton.push()
        }
        log.trace 'Pushed scene button'
    }

    log.trace 'Done setting scene'
}

private createChildDevice(deviceLabel) {
    app.updateLabel(deviceLabel)
    def child = getChildDevice(getDeviceID())

    if (!child) {
        child = addChildDevice(
            'jason0x43',
            'Virtual Momentary Switch',
            getDeviceID(),
            null,
            [name: getDeviceID(), label: deviceLabel, completedSetup: true]
        )
        log.info "Created switch [${child}]"
    } else {
    	child.label = app.label
        child.name = app.label
        log.info "Switch renamed to [${app.label}]"
    }
    
    return child
}

private getDeviceID() {
    return "SBSW_${app.id}"
}

private getGroupId(name) {
    return name.substring(10)
}

private getLightGroups() {
    def entries = settings.findAll { k, v -> k.startsWith('sceneLight') }
    return entries.keySet()
}

private getNextId() {
    def groups = getLightGroups()
    def nextId
    if (groups.size() > 0) {
        def ids = groups.collect { getGroupId(it).toInteger() }
        def maxId = ids.max()
        nextId = maxId + 1
    } else {
        nextId = 0
    }
}

private getLightState(name) {
    def id = getGroupId(name)
    def state = [ switch: settings["sceneSwitch${id}"] ]
    if (state) {
        state.level = settings["sceneLevel${id}"]
        state.hue = settings["sceneHue${id}"]
        state.saturation = settings["sceneSaturation${id}"]
        state.colorTemperature = settings["sceneColorTemperature${id}"]
    }
    return state
}

private supportsColor(name) {
    return settings[name].any { 
        it.supportedCommands.any { it.name == 'setHue' }
    }
}

private supportsColorTemp(name) {
    return settings[name].any { 
        it.supportedCommands.any { it.name == 'setColorTemperature' }
    }
}

private supportsLevel(name) {
    return settings[name].any { 
        it.supportedCommands.any { it.name == 'setLevel' }
    }
}
