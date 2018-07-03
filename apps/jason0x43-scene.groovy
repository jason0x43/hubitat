/**
 * Scene
 *
 * Author:  Jason Cheatham <j.cheatham@gmail.com>
 * Last updated: 2018-06-03, 16:33:50-0400
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
        section('Name') {
            label(title: 'Scene name', type: 'string', required: true)
        }

        getLightGroups().each {
            def name = it
            def id = name.substring(10)
            def state = sprintf('sceneSwitch%s', id)
            def level = sprintf('sceneLevel%s', id)

            section('Lights') {
                input(name: name, type: 'capability.switch', multiple: true, submitOnChange: true)
                input(name: state, title: 'On/off', type: 'bool', submitOnChange: true)

                if (settings[state] && supportsLevel(name)) {
                    input(name: level, title: 'Level', type: 'number')
                }
            }
        }

        def name = sprintf('sceneLight%d', getNextId())

        section('Lights') {
            input (name: name, type: 'capability.switch', multiple: true, submitOnChange: true)
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    // Create a switch to activate the scene
    def child = createChildDevice(app.label)
    subscribe(child, 'switch.on', setScene)
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
    
    // Create a switch to activate the scene
    def child = createChildDevice(app.label)
    subscribe(child, 'switch.on', setScene)
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

            if (state.switch) {
                if (currentSwitch != 'on') {
                    light.on()
                }

                def currentLevel = light.currentLevel
                if (state.level != null && currentLevel != null && state.level != currentLevel) {
                    light.setLevel(state.level)
                }
            } else if (currentSwitch == 'on') {
                light.off()
            }
        }
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
    }
    return state
}

private supportsLevel(name) {
    return settings[name].any { 
        it.supportedCommands.any { it.name == 'setLevel' }
    }
}
