/**
 *  Web Dashboard 2
 *
 *  Copyright 2014 Alex Malikov
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
    name: "Web Dashboard 2",
    namespace: "625alex",
    author: "Alex Malikov",
    description: "Web Dashboard 2",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    oauth: true)


preferences {
	section("Allow control of these things...") {
        input "switches", "capability.switch", title: "Which Switches?", multiple: true, required: false
        input "dimmers", "capability.switchLevel", title: "Which Dimmers?", multiple: true, required: false
        input "locks", "capability.lock", title: "Which Locks?", multiple: true, required: false
    }
    
    section("View state of these things...") {
        input "contacts", "capability.contactSensor", title: "Which Contact?", multiple: true, required: false
        input "presence", "capability.presenceSensor", title: "Which Presence?", multiple: true, required: false
        input "temperature", "capability.temperatureMeasurement", title: "Which Temperature?", multiple: true, required: false
    }
    
    section("Dashboard Preferences...") {
    	input "theme", title: "Theme", "enum", multiple: false, required: true, defaultValue: "Color", metadata: [values: ["Color", "Black and White", "Grey"]]
        input "showClock", title: "Show Clock", "enum", multiple: false, required: true, defaultValue: "Yes", metadata: [values: ["Yes", "No"]]
        input "showMode", title: "Show Mode", "enum", multiple: false, required: true, defaultValue: "Yes", metadata: [values: ["Yes", "No"]]
        input "viewOnly", title: "View Only", "enum", multiple: false, required: true, defaultValue: "No", metadata: [values: ["Yes", "No"]]
        input "interval", "number", title:"Refresh Interval (in minutes)", required: true, defaultValue:2
        input "title", "text", title:"Dashboard Name"
    }
}

mappings {
    path("/data") {
		action: [
			GET: "list",
		]
	}
    path("/ui") {
		action: [
			GET: "html",
		]
	}
    path("/command") {
    	action: [
			GET: "command",
		]
    }
}

def command() {
	if (isViewOnly()) {
		return false;
	}
	
	log.debug "command received with params $params"
    
    def id = params.id
    def type = params.type
    def value = params.value
    
    def device
    def endState
    def attribute
    
    if (value == "toggle" && (type == "dimmer" || type == "switch")) {
    	device = (type == "dimmer" ? dimmers : switches)?.find{it.id == id}
        attribute = "switch"
        
        log.debug "command toggle for dimmer/switch $device"
        if (device) {
            if(value == "toggle") {
                if(device.currentValue('switch') == "on") {
                    device.off()
                    endState = "off"
                } else {
                    device.on()
                    endState = "on"
                }
            } else if (value == "on") {
                device.on()
                endState = "on"
            } else if (value == "off") {
                device.off()
                endState = "off"
            }
        }
    } else if (type == "dimmer" && value == "0") {
    	device = dimmers?.find{it.id == id}
        attribute = "switch"
        endState = "off"
        
        if (device) {
        	device.setLevel(0)
        	device.off()
        }
    } else if (type == "dimmer") {
    	device = dimmers?.find{it.id == id}
        attribute = "level"
        endState = Math.min(value as Integer, 99) as String
        
        if (device) {
        	device.setLevel(Math.min(value as Integer, 99))
        }
    } else if (type == "lock") {
    	device = locks?.find{it.id == id}
        attribute = "lock"
        
        if (device) {
        	log.debug "current lock status ${device.currentValue('lock')}"
        	if(device.currentValue('lock') == "locked") {
                device.unlock()
                endState = "unlocked"
            } else {
                device.lock()
                endState = "locked"
            }
            
        }
    } else if (type == "mode") {
		setLocationMode(value)
	}
    
    def isUpdated = type == "mode" ? true : waitForUpdate(device, endState, attribute)
    
    def response = [:]
    
    if (isUpdated) {
    	response.status = "ok"
    } else {
    	response.status = "refresh"
    }
    log.debug "isUpdated for $device : $isUpdated"
    
    render contentType: "application/javascript", data: "${params.callback}(${response.encodeAsJSON()})"
}

/*
	Hacked varsion of long poll. Will wait up to 15 seconds for the status to update. If times out without
    ever updating, the page will be forsed to refresh right away.
*/
def waitForUpdate(device, endState, attribute) {
	log.debug "about to check $device attribute $attribute for $endState"
	if (device && endState && attribute) {
    	for (def i = 0; i < 5 ; i++ ) {
            if (device.currentValue(attribute)?.toString() == endState) {
            	return true
            } else {
            	log.debug "checking #$i, expected $device attribute $attribute to be $endState but was ${device.currentValue(attribute)}"
            	pause(3000)
            }
        }
    	
    }
    return false
}

def isViewOnly() {
	return viewOnly == "Yes"
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
	if (!state.accessToken) {
    	createAccessToken()
    }
    subscribe(app, getURL)
    getURL(null)
}

def getURL(e) {
	def url = "https://graph.api.smartthings.com/api/smartapps/installations/${app.id}/ui?access_token=${state.accessToken}"
    log.debug "app url: $url"
}

def index() {
	["index", "list", "html"]
}

def list() {
	render contentType: "application/javascript", data: "${params.callback}(${data().encodeAsJSON()}})"
}

def data() {
    [
    	locks: locks?.collect{[type: "lock", id: it.id, name: it.displayName, status: it.currentValue('lock') == "locked" ? "locked" : "unlocked"]}.sort{it.name},
        switches: switches?.collect{[type: "switch", id: it.id, name: it.displayName, status: it.currentValue('switch')]}.sort{it.name},
        dimmers: dimmers?.collect{[type: "dimmer", id: it.id, name: it.displayName, status: it.currentValue('switch'), level: it.currentValue('level')]}.sort{it.name},
        contacts: contacts?.collect{[type: "contact", id: it.id, name: it.displayName, status: it.currentValue('contact')]}.sort{it.name},
        presence: presence?.collect{[type: "presence", id: it.id, name: it.displayName, status: it.currentValue('presence')]}.sort{it.name},
        temperature: temperature?.collect{[type: "temperature", id: it.id, name: it.displayName, status: it.currentValue('temperature')]}.sort{it.name},
    ]
}

def html() {
    render contentType: "text/html", data: "<!DOCTYPE html><html><head>${head()}</head><body>${body()}\n${script()}</body></html>"
}

def head() {
	"""
    <meta name="viewport" content="width=device-width" />
    <title>${title ?: location.name} Dashboard</title>
    <link href="//maxcdn.bootstrapcdn.com/font-awesome/4.2.0/css/font-awesome.min.css" rel="stylesheet">
    <link rel="stylesheet" href="//code.jquery.com/mobile/1.4.4/jquery.mobile-1.4.4.min.css" />
    
    <script src="//code.jquery.com/jquery-2.1.1.min.js"></script>
    <script src="//code.jquery.com/mobile/1.4.4/jquery.mobile-1.4.4.min.js"></script>
    
    ${style()}
    ${themes()}
    ${media()}
    """
}                                                              

def script() {
"""
<script>
	\$(function() {
        \$("body").bind("contextmenu",function(){
           return false;
        });
        
    	\$(".lock, .switch").click(function() {
			${isViewOnly() ? "return false;" : ""}
            animateToggle(\$(this));
            sendCommand(\$(this).attr("deviceType"), \$(this).attr("deviceId"), "toggle");
		});
        
        \$(".dimmer").on( 'slidestop', function( e ) {
        	//animateSliderToOff(\$(this).closest(".st-tile"));
            var val = \$(this).find("input").val();
            if (val == 0) {
            	animateDimmer(\$(this).closest(".st-tile"), "fa fa-toggle-off");
            } else {
                animateDimmer(\$(this).closest(".st-tile"), "fa fa-toggle-on");
            }
            sendCommand("dimmer", \$(this).find("input").attr("deviceId"), \$(this).find("input").val());
        });
        
        \$(".dimmer .st-icon").click(function() {
            animateToggle(\$(this).closest(".st-tile"));
            sendCommand("dimmer", \$(this).closest(".st-tile").attr("deviceId"), "toggle");
            var status = \$(this).closest(".st-tile").attr("deviceStatus");
            if (status == "off") {
            	var level = \$(this).closest(".st-tile").find("input").attr("deviceLevel");
            	\$(this).closest(".st-tile").attr("deviceStatus", "on");
                \$(this).closest(".st-tile").find("input").val(level).slider("refresh");
            } else {
            	\$(this).closest(".st-tile").find("input").val(0).slider("refresh");
                \$(this).closest(".st-tile").attr("deviceStatus", "off");
            }
            
		});
        
        \$(".refresh").click(function() {
        	animateClick(\$(this));
            refresh();
		});
        
        \$("#mode-select").change(function () {
        	animateClick(\$(".mode"));
            \$(this).find( "option:selected" ).each(function() {
            	sendCommand("mode", "mode", \$( this ).text())
            });
            /*\$("#mode_mode").find(".spin").css({visibility: "visible"});*/
		});
	});
    
    function animateToggle(element) {
        var oldIcon = element.find(".st-icon i").attr("class");
        var newIcon = toggleIcon(oldIcon);
        spin(element).animate({opacity: .5}, 300).find(".st-icon i").removeClass(oldIcon).addClass(newIcon).closest(element).animate({opacity: 1}, 200);
        return element;
    }
    
    function animateDimmer(element, newIcon) {
        var oldIcon = element.find(".st-icon i").attr("class");
        spin(element).animate({opacity: .5}, 300).find(".st-icon i").removeClass(oldIcon).addClass(newIcon).closest(element).animate({opacity: 1}, 200);
        return element;
    }
    
    function animateClick(element) {
        spin(element).animate({opacity: .5}, 300).animate({opacity: 1}, 200);
    }
    
    function sendCommand(type, id, value) {
    	//alert("&type=" + type + "&id=" + id + "&value=" + value);
        var url = "command/?" + window.location.href.slice(window.location.href.indexOf('?') + 1) + "&type=" + type + "&id=" + id + "&value=" + value;
            \$.getJSON(url + "&callback=?")
            .done(function( data ) {
            	if (data.status == "ok") {
                	var elementId = "#" + type + "_" + id;
                	\$(elementId).find(".spin").animate({opacity: 0}, 1000);
                	setTimeout(function() { refresh(); }, 30000);
                } else if (data.status == "refresh") {
                	refresh();
                }
            }).fail(function( jqxhr, textStatus, error ) {
                //alert( "Request Failed: " + textStatus + ", " + error );
            });
    }
    
   	function refresh() {
    	\$(".refresh .st-icon").addClass("fa-spin");
        location.reload();
    }
    
    function spin(element) {
    	element.find(".spin").animate({opacity: .5}, 500);
        return element;
    }
    
    function toggleIcon(icon) {
    	var icons = {
        "fa fa-toggle-off" : "fa fa-toggle-on",
        "fa fa-toggle-on" : "fa fa-toggle-off",
        "fa fa-lock" : "fa fa-unlock-alt",
        "fa fa-unlock-alt" : "fa fa-lock"
        }
        
        return icons[icon];
    }
    
    function refreshMyself() {
        setTimeout(function() {refresh(); }, 1000 * 60 * $interval);
	}
        
    refreshMyself();
    
</script>
"""
}

def style() {
"""
<style type="text/css">
.st-tile {
	cursor:defaut!important;
}
.refresh {
	cursor: pointer;
}
.lock, .switch, .dimmer {
	cursor: ${isViewOnly() ? "default" : "pointer"};
}

.st-title {
	color: white;
    font-size: 14px;
    line-height: 16px;
    font-weight:400;
    font-style: normal;
    text-shadow: none;
}

.st-container {
   overflow: hidden;
   margin: 5px;
}

.st-tile {
   float: left;
   position: relative;
   width: 10%;
   padding-bottom: 10%;
}

.st-icon {
    color: white;
    height: 3em;
    width: 3em;
    left: 50%;
    top: 50%;
    margin-left: -1.5em;
    margin-top: -1.5em;
    position: absolute;
    text-align: center;
}

.fa {
	font-size:3em;
}

.st-tile-content {
   position: absolute;
   left: 5px;
   right: 5px;
   top: 5px;
   bottom: 5px;
   overflow: hidden;
   background-color: grey;
   padding: 5px;
}

.x2 {
	width: 20%;
	padding-bottom: 10%;
}

.clock .st-tile-content {
	background-color: #ff0097;
}

.presence .st-tile-content {
	background-color: #2d89ef;
}

.lock .st-tile-content {
	background-color: #da532c;
}

.switch .st-tile-content {
	background-color: #99b433;
}

.dimmer .st-tile-content {
	background-color: #1e7145;
}

.contact .st-tile-content {
	background-color: #00aba9;
}

.refresh .st-tile-content {
	background-color: #e3a21a;
}

.temperature .st-tile-content {
	background-color: #ffc40d;
}

.mode .st-tile-content {
	background-color: #603cba;
}

.r45 {
    -moz-transform:rotate(45deg);
    -webkit-transform:rotate(45deg);
    -o-transform:rotate(45deg);
    -ms-transform:rotate(45deg);
    transform:rotate(45deg)
}

#clock {
	height: 1em;
    font-size:2em;
    line-height:1em;
    margin-top:-0.5em;
}

.st-container {
	margin: 0;
    padding: 0;
    padding-bottom: 5px;
    background-color: #1d1d1d!important;
}

.ui-content {
	padding:5px 0 0 5px;;
}

div.st-tile-content {
	#background-color:lightGrey!important;
}

.opaque {
	opacity: 0.3;
	-ms-filter:"progid:DXImageTransform.Microsoft.Alpha(Opacity=30)";
	filter: alpha(opacity=30);
}

* {
	text-shadow:none!important;
    font-family: Calibri, "Arial Black", Gadget, "Segoe UI Light_","Open Sans Light",Verdana,Arial,Helvetica,sans-serif;
}

.temperature .st-icon {
	font-size:3em;
    left:0;
    margin:0;
    width:100%;
    height:100%;
    top:25%;
}

.spin {
	color: white;
	/*visibility: hidden;*/
    margin-right:3px;
    opacity: 0;
	-ms-filter:"progid:DXImageTransform.Microsoft.Alpha(Opacity=0)";
	filter: alpha(opacity=0);
    font-size:10px!important;
    position:absolute;
    bottom:7px;
    right:7px;
}

.ui-slider-track.ui-mini {
	margin-left:0;
    width:100%;
}

.ui-slider-popup {
	height:12px;
    width:20px;
    padding-top:0px;
    padding-bottom:3px;
    font-size:12px;
}

.ui-slider-handle {
	background-color:white;
}

.full-width-slider input {
    display: none;
}

.full-width-slider {
	/*width:100px;
    height:50px;
    left:50%;
    top:50%;
    margin-left:-50px;
    margin-top:20px;
    position: absolute;*/
    width:80%;
    height:50px;
    left:50%;
    top:50%;
    margin-left:-40%;
    margin-top:10%;
    position: absolute;
    
}
.full-width-select {
	width:100px;
    height:50px;
    left:50%;
    top:50%;
    margin-left:-50px;
    margin-top:-25px;
    position: absolute;
    
}

.full-width-select .ui-btn {
	background:none;
    color: white
}

.ui-btn {
	text-align:center!important;
}

.toggle {
	display:none;
}

.ui-btn-corner-all, .ui-btn.ui-corner-all, .ui-slider-track.ui-corner-all, .ui-flipswitch.ui-corner-all, .ui-li-count {
    border-radius: 1em!important;
}

.ui-slider-track.ui-mini .ui-slider-handle {
	height:20px;
    width:20px;
    margin:-11px 0 0 -11px;
}
</style>
"""
}

def themes() {
	if (theme == "Black and White") {
"""
<style>
.st-tile-content {
	background-color: black!important;
}
body {
	background-color: lightGrey!important;
}

.ui-slider-bg {
	background-color:white!important;
}
</style>
"""
	} else if (theme == "Grey") {
"""
<style>
body {
	background-color: #1d1d1d!important;
}
.st-tile-content {
	background-color: grey!important;
}

.ui-slider-bg {
	background-color:white!important;
}
</style>
"""
    } else {
"""
<style>
body {
	background-color: #1d1d1d!important;
}

.ui-slider-bg {
	background-color:white!important;
}

</style>
"""
    }
}

def media() {
"""
<style>
    @media only screen and (max-width : 480px) {
   /* Smartphone view: 3 tile */
   .st-tile {
      width: 33.3%;
      padding-bottom: 33.3%;
   }
   .st-tile-content {
	  left:2px;
      right:2px;
      top:2px;
      bottom:2px;
   }
   .ui-content {
   	  padding:0;
   }
}

@media only screen and (max-width : 565px) and (min-width : 481px) {
   /* Tablet view: 4 tiles */
   .st-tile {
      width: 25%;
      padding-bottom: 25%;
   }
   .st-tile-content {
	  left:2px;
      right:2px;
      top:2px;
      bottom:2px;
   }
   .ui-content {
   	  padding:0px;
   }
}

@media only screen and (max-width : 650px) and (min-width : 566px) {
   /* Tablet view: 5 tiles */
   .st-tile {
      width: 20%;
      padding-bottom: 20%;
   }
   .st-tile-content {
	  left:3px;
      right:3px;
      top:3px;
      bottom:3px;
   }
   .ui-content {
   	  padding:3px;
   }
}


@media only screen and (max-width : 850px) and (min-width : 651px) {
   /* Small desktop / ipad view: 6 tiles */
   .st-tile {
      width: 16.6%;
      padding-bottom: 16.6%;
   }
}

@media only screen and (max-width : 1050px) and (min-width : 851px) {
   /* Small desktop / ipad view: 7 tiles */
   .st-tile {
      width: 14.2%;
      padding-bottom: 14.2%;
   }
}

@media only screen and (max-width : 1290px) and (min-width : 1051px) {
   /* Medium desktop: 8 tiles */
   .st-tile {
      width: 12.5%;
      padding-bottom: 12.5%;
   }
   
}
</style>
"""
}

def body() {
	def devices = ""
    data().values().flatten().each{devices = devices + renderDevice(it)}
    
	"""
    <div class="st-container" data-role="page">
    	<div data-role="content" data-theme="c">
            ${renderRefresh()}
            ${showMode == "Yes" ? renderMode() : ""}
            ${showClock == "Yes" ? renderClock() : ""}
            $devices
		</div>
    </div>
    """
}

def renderClock() {
"""
<div id="refresh" class="st-tile clock">
	<div class="st-tile-content">
    	<div class="st-title">
        	Time
        </div>
        <div class="st-icon" id="clock">
        	*
        </div>
	</div>
</div>

<script>
function startTime() {
    var today=new Date();
    var h=today.getHours();
    var m=today.getMinutes();
    var s=today.getSeconds();
    m = checkTime(m);
    s = checkTime(s);
    document.getElementById('clock').innerHTML = h+":"+m;
    var t = setTimeout(function(){startTime()},500);
}

function checkTime(i) {
    if (i<10) {i = "0" + i};  // add zero in front of numbers < 10
    return i;
}
startTime();
</script>
"""
}

def renderRefresh() {
"""
<div id="refresh" class="st-tile refresh" deviceId="refresh" deviceType="refresh">
	<div class="st-tile-content">
    	<div class="st-title">
        	Refresh
        </div>
        <div class="st-icon">
        	<i class="fa fa-refresh"></i>
        </div>
	</div>
</div>
"""
}

def renderMode() {
    def modes = """<option value="$location.mode" selected="selected">$location.mode</option>"""

    location.modes.each {
        if (location.mode.toString() != it.toString()) {
            modes = modes + """<option value="$it">$it</option>\n"""
        }
    }
    
"""
<div id="mode_mode" class="st-tile mode">
	<div class="st-tile-content">
    	<div class="st-title">
        	Mode
        </div>
       
        <div class="full-width-select" style="${isViewOnly() ? "display:none": ""}">
            <label for="mode-select" class="ui-hidden-accessible">Mini sized:</label>
            <select name="mode-select" id="mode-select" data-native-menu="false" data-mini="true" data-iconpos="none">
                $modes
            </select>
        </div>
        ${isViewOnly() ? """<div class="st-icon" style="font-size:2em; height:1em;line-height:1em; margin-top:-0.5em;">$location.mode</div>""" : ""}
        <i class="spin fa fa-refresh fa-spin"></i>
	</div>
</div>
"""
}

def renderTemperature(device) {
"""
<div id="device_$device.id" class="st-tile $device.type">
	<div class="st-tile-content">
    	<div class="st-title">
        	$device.name
        </div>
        <div class="st-icon">
        	$device.status°
        </div>
	</div>
</div>
"""
}

def renderDimmer(device) {
"""
<div id="dimmer_$device.id" class="st-tile $device.type" 
	deviceId="$device.id" deviceType="dimmer" deviceLevel="$device.level" deviceStatus="$device.status">
	<div class="st-tile-content">
    	<div class="st-title">
        	$device.name
        </div>
        <div class="st-icon">
        	<i class="fa ${device.status == "on" ? "fa-toggle-on" : "fa-toggle-off"}"></i>
        </div>
        <div class="full-width-slider" style="${isViewOnly() ? "display:none" : ""}">
            <label for="dimmer_$device.id" class="ui-hidden-accessible">Level:</label>
            <input name="dimmer_$device.id" id="dimmer_$device.id" min="0" max="100" value="${device.status == "on" ? device.level : 0}" type="range" 
            deviceLevel="$device.level"
            data-show-value="false" data-mini="true" data-popup-enabled="true" 
            data-disabled="${isViewOnly()}"
            data-highlight="true" step="5" deviceId="$device.id">
		</div>
        <i class="spin fa fa-refresh fa-spin"></i>
	</div>
</div>
"""
}

def renderSwitch(device) {
"""
<div id="switch_$device.id" class="st-tile switch"  deviceId="$device.id" deviceType="switch">
	<div class="st-tile-content">
    	<div class="st-title">
        	$device.name
        </div>
        <div class="st-icon">
        	<i class="fa ${device.status == "on" ? "fa-toggle-on" : "fa-toggle-off"}"></i>
        </div>
        <i class="spin fa fa-refresh fa-spin"></i>
	</div>
</div>
"""
}

def renderLock(device) {
"""
<div id="lock_$device.id" class="st-tile lock" deviceId="$device.id" deviceType="lock">
	<div class="st-tile-content">
    	<div class="st-title">
            $device.name
        </div>
        <div class="st-icon">
        	<i class="fa ${device.status == "locked" ? "fa-lock" : "fa-unlock-alt"}"></i>
        </div>
        <i class="spin fa fa-refresh fa-spin"></i>
	</div>
</div>
"""
}

def renderPresence(device) {
"""
<div id="device_$device.id" class="st-tile $device.type">
	<div class="st-tile-content">
    	<div class="st-title">
        	$device.name
        </div>
        <div class="st-icon">
        <i class="fa ${device.status == "present" ? "fa-map-marker" : "opaque fa-map-marker"}"></i>
        </div>
	</div>
</div>
"""
}

def renderContact(device) {
"""
<div id="device_$device.id" class="st-tile $device.type">
	<div class="st-tile-content">
    	<div class="st-title">
        	$device.name
        </div>
        <div class="st-icon">
        	<i class="r45 fa ${device.status == "open" ? "fa-expand" : "fa-compress"}"></i>
        </div>
	</div>
</div>
"""
}

def renderDevice(device) {
	if (device.type == "dimmer") return renderDimmer(device)
    if (device.type == "switch") return renderSwitch(device)
    if (device.type == "lock") return renderLock(device)
    if (device.type == "presence") return renderPresence(device)
    if (device.type == "contact") return renderContact(device)
    if (device.type == "temperature") return renderTemperature(device)
    else return device
    
}
