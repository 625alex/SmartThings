/**
 *  Dim-a-Hue
 *
 *  Copyright 2015 Alex Malikov
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
definition(
    name: "Dim-a-Hue",
    namespace: "625alex",
    author: "Alex Malikov",
    description: "Control Hue with a set of dimmers. Requires 1+ color light and 1-3 dimmers.",
    category: "Fun & Social",
    iconUrl: "http://cdn.device-icons.smartthings.com/lights/philips/hue-multi.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/lights/philips/hue-multi@2x.png",
    iconX3Url: "http://cdn.device-icons.smartthings.com/lights/philips/hue-multi@2x.png")


preferences {
	section("Control these light...") {
		input "slaves", "capability.colorControl", title: "Choose lights with control", required: false, multiple: true
	}
	
	section("Controllers...") {
		input "masterHue", "capability.switchLevel", title: "Choose dimmer that controls hue", required: false, multiple: false
		input "masterSaturation", "capability.switchLevel", title: "Choose dimmer that controls saturation", required: false, multiple: false
		input "masterLevel", "capability.switchLevel", title: "Choose dimmer that controls brightness", required: false, multiple: false
		paragraph ""
		input "masterSwitch", "capability.switch", title: "Choose switch that turns color lights on and off", required: false, multiple: false
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(masterHue, "level", hueHandler)
	subscribe(masterSaturation, "level", saturationHandler)
	subscribe(masterLevel, "level", levelHandler)
	subscribe(masterSwitch, "switch", switchHandler)
}

def hueHandler(e) {
	log.debug "setting hue of $slaves to $e.value%"
	slaves?.setHue(e.value as Integer)
}

def saturationHandler(e) {
	log.debug "setting saturation of $slaves to $e.value%"
	slaves?.setSaturation(e.value as Integer)
}

def levelHandler(e) {
	log.debug "setting level of $slaves to $e.value%"
	slaves?.setLevel(e.value as Integer)
}

def switchHandler(e) {
	log.debug "setting switch of $slaves to $e.value"
	slaves?."$e.value"()
}

