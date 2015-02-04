definition(
    name: "Pushbullet Service Manager",
    namespace: "625alex",
    author: "Alex Malikov",
    description: "Pushbullet Service Manager",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences {
    page(name: "Credentials", title: "Enter API key", nextPage: "importDevicesPage", install: false, unintall: true) {
    	section("Enter API key...") {
            input("apiKey", "text", title: "API Key", description: "Your Pushbullet API Key", required: true, install: true, uninstall: true)
        }
    }
    
	page(name: "importDevicesPage", title: "Existing Pushbullet Devices", content: "importDevicesList", install: true)
    
}

def importDevicesList()
{
	log.debug "enter importDevicesList"
    def data = [:]
    discoverDevices()?.each {
    	data[it.iden] = it.nickname
    }
    
    log.debug "state.stIden $state.stIden"
    data.remove(state.stIden)
    
	log.debug "Devices available for import: ${data}"

	return dynamicPage(name: "importDevicesPage", title: "Import the following devices", install: true, uninstall: true) {
		section("Devices") {
			input "selectedDevices", "enum", title: "Select Device(s)", required: false, multiple: false, options: data
		}
	}
}

def getLocalIden(def iden) {
	location.name + "|" + iden
}

def discoverDevices() {
	log.debug "entered discoverDevices"
	
    def url = "https://${settings.apiKey}@api.pushbullet.com/v2/devices"
    
    log.debug "url: $url"
    
    httpGet(uri: url) { response ->
      def devs = []
      if (response?.data?.devices) {
      	response.data.devices.each { device ->
        	if (device.pushable && device.active)
			devs << device
        }
      }
      
      devs.sort() {it.nickname}
      log.debug "Active Devices found: ${devs}"
	  state.discoveredDevices = devs
      return devs
    }
}

def importDevice(def iden) {
	log.debug "import devices for $iden"
	
	if (iden) {
		def device = state.discoveredDevices?.find {it.iden == iden}
		if (device) {
			if (getChildDevice(getLocalIden(iden))) {
				log.debug "device with iden $iden already exists"
			} else {
				createChilDevice(iden, device.nickname)
				log.debug "imported device with nickname $device.nickname, iden $iden"
			}
		}
	}
}

def createChilDevice(def iden, def nickname) {
	log.debug "enter createChilDevice settings.apiKey: $settings.apiKey"
	
	def newDevice = addChildDevice("625alex", "Pushbullet", getLocalIden(iden), null, [name: nickname, label: "Pushbullet " + nickname])
	
	newDevice.setKey(settings.apiKey)
	newDevice.setNickname(nickname)
	newDevice.setIden(iden)
	
	log.debug "created child device with iden: $iden"
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {	
    log.debug "Updated with settings: ${settings}"
    unschedule()
    unsubscribe()
	initialize()
	
	log.debug "settings.selectedDevices $settings.selectedDevices"
	def selectedDevices = [selectedDevices].flatten()
	selectedDevices.each {
		importDevice(it)
	}
}

def initialize() {
    log.debug "initialize with settings: ${settings}"
	createSTDevice()
    schedulePoll()
}

def uninstalled() {
	log.debug "enter uninstalled"
	def devices = getChildDevices()
	log.trace "deleting ${devices.size()} Pushbullet(s)"
	devices.each {
		deleteChildDevice(it.deviceNetworkId)
	}
    
    deleteSTDevice()
    
    unschedule()
}

def createSTDevice() {
	log.debug "enter createSTDevice"
	if (state.stIden) {
		log.debug "createSTDevice was already called, created $state.stIden" 
        return null
	}
    
    log.debug "start createSTDevice" 
    
    def url = "https://${settings.apiKey}@api.pushbullet.com/v2/devices"
	    
	def successClosure = { response ->
      createChilDevice(response.data?.iden, response.data?.nickname)
	  state.stIden = response.data?.iden
	  state.lastPoll = response.data?.created
      log.debug "Create device request was successful, iden $state.stIden"
    }
    
    def postBody = [
        user: settings.apiKey,
        u: settings.apiKey,
        type: "stream",
        nickname: "ST"
    ]
    
    def params = [
      uri: url,
      success: successClosure,
      body: postBody
    ]
    
    httpPost(params)
    
    return state.stIden
}

def deleteSTDevice() {
	log.debug "delete STDevice $state.stIden" 
	if (state.stIden) {
		def url = "https://${settings.apiKey}@api.pushbullet.com/v2/devices/${state.stIden}"
		
		httpDelete(uri: url) { response ->
			log.debug "deleted STDevice"
		}
	}
}

def schedulePoll() {
	log.debug "running schedulePoll"
    poll()
    
    schedule("0 * * * * ?", schedulePoll)
}

def poll() {
	log.debug "enter poll"
    unschedule()
    
    def url = "https://${settings.apiKey}@api.pushbullet.com/v2/pushes?modified_after=${state.lastPoll ?: 0}"
    log.debug "polling url: $url" 

    httpGet(uri: url) { response ->
		def pushes = response?.data?.pushes
        log.debug "poll response: $pushes"
		if (pushes) {
        	log.debug "last poll 1: $state.lastPoll"
			state.lastPoll = pushes.modified.max()
            log.debug "last poll 2: $state.lastPoll"
			pushes.each {
				def child = getChildDevice(getLocalIden(it.target_device_iden))
				log.debug "child exists for push? $child"
				if (child) {
                	it.pollUrl = url
					child.pushReceived(it)
				}
			}
		}
    }
}

def push(def message, def title, def iden) {
	log.debug "enter push. message: $message, iden: $iden" 
    def url = "https://${settings.apiKey}@api.pushbullet.com/v2/pushes"
	    
	def successClosure = { response ->
      log.debug "Push request was successful, $response.data"
    }
    
    def postBody = [
        user: settings.apiKey,
        u: settings.apiKey,
        device_iden: iden,
        type: "note",
        title: title,
      	body: message
    ]
    
    def params = [
      uri: url,
      success: successClosure,
      body: postBody
    ]
    
    httpPost(params)
}
