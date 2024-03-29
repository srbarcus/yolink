/***
 *  YoLink™ Siren (YS7103-UC)
 *  © (See copyright()) Steven Barcus. All rights reserved.
 *  THIS SOFTWARE IS NEITHER DEVELOPED, ENDORSED, OR ASSOCIATED WITH YoLink™ OR YoSmart, Inc.
 *   
 *  DO NOT INSTALL THIS DEVICE MANUALLY - IT WILL NOT WORK. MUST BE INSTALLED USING THE YOLINK DEVICE SERVICE APP   
 *   
 *  Developer retains all rights, title, copyright, and interest, including patent rights and trade
 *  secrets in this software. Developer grants a non-exclusive perpetual license (License) to User to use
 *  this software under this Agreement. However, the User shall make no commercial use of the software without
 *  the Developer's written consent. Software Distribution is restricted and shall be done only with Developer's written approval.
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied. 
 * 
 *  1.0.1: Remove superfluous code
 *  1.0.2: (skipped)
 *  1.0.3: Fixed clientVersion()
 *  1.0.4: Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 *         - Correct attribute types
 *  1.0.5: def temperatureScale()
 *  1.0.6: Fix donation URL
 *  1.0.7: Added getSetup()
 *  2.0.0: Reengineer driver to use centralized MQTT listener due to new YoLink service restrictions 
 *  2.0.1: - Add 'Switch' capability to support Hubitat Dashboard
 *         - Correct Alarm state 
 *         - Recognize when device is turned off
 *  2.0.2: Support diagnostics, correct various errors, make singleThreaded
 *  2.0.3: Added formatted "signal" attribute as rssi & " dBm"
 *         - Added capability "SignalStrength"
 *  2.0.4: Prevent Service app from waiting on device polling completion
 *  2.0.5: Updated driver version on poll
 *  2.0.6: Handle messages 'setMute' and 'setTimeZone'
 *         - Add attributes: 'mute', 'muteDuration', 'muteRemaining'
 *  2.0.7: Support "setDeviceToken()"
 *         - Update copyright
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.7"}
def copyright() {return "<br>© 2022-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "Siren (YS7103-UC) v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink Siren Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"				
		capability "Battery"
        capability "Alarm"                  // ENUM ["strobe", "off", "both", "siren"]    
        capability "PowerSource"            // ENUM ["battery", "dc", "mains", "unknown"]  API "usb" = "mains"
        capability "Switch"                 // ENUM ["on", "off"]
        capability "SignalStrength"
       
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:[true, false]]] 
        command "reset"          
             
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"          
        attribute "signal", "String"
        attribute "lastPoll", "String"
        attribute "lastResponse", "String"    
        attribute "reportAt", "String"

        attribute "volume", "Number" 
        attribute "alarmDuration", "Number"
        attribute "mute", "String"   
        attribute "muteDuration", "Number"   
        attribute "muteRemaining", "Number"   
        }
   }

void setDeviceToken(token) {
    if (state.token != token) { 
      log.warn "Device token '${state.token}' changed to '${token}'"
      state.token=token
    } else {    
      logDebug("Device token remains set to '${state.token}'")
    }    
 }

void ServiceSetup(Hubitat_dni,homeID,devname,devtype,devtoken,devId) {
	state.debug = false	
    
    state.my_dni = Hubitat_dni      
    state.homeID = homeID    
    state.name = devname
    state.type = devtype
    state.token = devtoken
    rememberState("devId", devId)  
    	
	log.info "ServiceSetup(Hubitat dni=${state.my_dni}, Home ID=${state.homeID}, Name=${state.name}, Type=${state.type}, Token=${state.token}, Device Id=${state.devId})"
	    
    reset()      
 }

public def getSetup() {
    def setup = [:]
        setup.put("my_dni", "${state.my_dni}")                   
        setup.put("homeID", "${state.homeID}") 
        setup.put("name", "${state.name}") 
        setup.put("type", "${state.type}") 
        setup.put("token", "${state.token}") 
        setup.put("devId", "${state.devId}") 
    return setup
}

public def isSetup() {
    return (state.my_dni && state.homeID && state.name && state.type && state.token && state.devId)
}

def installed() {
   log.info "Device Installed"
   rememberState("driver", clientVersion())    
 }

def updated() {
   log.info "Device Updated" 
   rememberState("driver", clientVersion()) 
 }

def uninstalled() {
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def poll(force=null) {
    logDebug("poll(${force})")

    def lastPoll
    def cur_time = now()
    def min_seconds = 10                     // To avoid unecessary load on YoLink servers, limit rate of polling
    def min_interval = min_seconds * 1000    // Convert to milliseconds

    if (force != null) {
       logDebug("Forcing poll")
       state.lastPoll = cur_time - min_interval
    }

    lastPoll = state.lastPoll

    def min_time = lastPoll + min_interval

    if (cur_time < min_time ) {
       log.warn "Polling interval of once every ${min_seconds} seconds exceeded, device was not polled."	
    } else {
       pollDevice()
       state.lastPoll = now()
    }
 }

def pollDevice(delay=1) {
    rememberState("driver", clientVersion())
    runIn(delay,getDevicestate)
    def date = new Date()
    sendEvent(name:"lastPoll", value: date.format("MM/dd/yyyy hh:mm:ss a"), isStateChange:true)
 }

def temperatureScale(value) {}

def debug(value) { 
   rememberState("debug",value)
   if (value == "true") {
     log.info "Debugging enabled"
   } else {
     log.info "Debugging disabled"
   }    
}

def strobe () {  
   setSiren("True")       
}

def both () {  
   setSiren("True")       
}

def siren () {  
   setSiren("True")       
}

def on () {
   setSiren("True") 
   }       

def off () {
   setSiren("False")  
}    
    
def setSiren(setState) {   
   def params = [:] 
   def alarm = [:] 
    
   alarm.put("alarm", setState.toBoolean())    
   params.put("state", alarm)      
    
   def request = [:] 
   request.put("method", "${state.type}.setState")      
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
      def object = parent.pollAPI(request, state.name, state.type)       
    } catch (e) {	
        log.error "setSiren() exception: $e"
        lastResponse("Error ${e}")     
        sendEvent(name:"alarm", value: "unknown", isStateChange:true)
	} 
          
    getDevicestate()
}   

def getDevicestate() {  
	logDebug("getDevicestate() obtaining device state")
    
	boolean rc=false	//DEFAULT: Return Code = false
    
	try {  
        def request = [:]
            request.put("method", "${state.type}.getState")                   
            request.put("targetDevice", "${state.devId}") 
            request.put("token", "${state.token}") 
        
        def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("getDevicestate()> pollAPI() response: ${object}")     
            
            if (successful(object)) {                
                parseDevice(object)                     
                rc = true	
                rememberState("online", "true") 
                lastResponse("Success") 
            } else {  //Error
               pollError(object)                
            }     
        } else {
            log.error "No response from API request"
            lastResponse("No response from API") 
        } 
	} catch (groovyx.net.http.HttpResponseException e) {	
            rc = false                        
			if (e?.statusCode == UNAUTHORIZED_CODE) { 
                lastResponse("Unauthorized")                
            } else {
                    lastResponse("Exception $e")                
					logDebug("getDevices() Exception $e")
			}           
	}
    
	return rc
}    

def parseDevice(object) {   
    def alarm = object.data.state
    def volume = object.data.soundLevel    
    def battery = parent.batterylevel(object.data.battery) 
    def powerSource = object.data.powerSupply 
    if (powerSource == "usb") {powerSource = "mains"}
    def alarmDuration = duration(object.data.alarmDuation)   //API message has a spelling error
    def mute = object.data.mute
    def muteDuration = duration(object.data.muteDuration)
    def muteRemaining = duration(object.data.muteRemaining)
    def firmware = object.data.version.toUpperCase()
    def rssi = object.data.loraInfo.signal
    
    rememberState("volume",volume)
    rememberState("battery",battery)
    rememberState("powerSource",powerSource)    
    rememberState("alarmDuration",alarmDuration)
    rememberState("mute",mute)
    rememberState("muteDuration",muteDuration)
    rememberState("muteRemaining",muteRemaining)
    rememberState("firmware",firmware)    
    fmtSignal(rssi)
    
    setAlarmState(alarm)
                   
    logDebug("Device State: online(${online}), " +
             "Alarm(${state.alarm}), " +
             "Switch(${state.switch}), " +
             "Volume(${volume}), " +             
             "Battery(${battery}), " + 
             "Power Source(${state.powerSource}), " +
             "Alarm Duration(${alarmDuration}), " +
             "Firmware(${firmware}), " +
             "RSSI(${rssi})")    
}   

def setAlarmState(alarm) {
    def swstate
    def online = true
       
    switch(alarm) {		
        case "normal":       
            alarm = "off" 
            swstate = "off" 
 		    break;   
            
        case "alert":
            alarm = "both" 
            swstate = "on"  
            break;   
            
        case "off":
        case "disabled":
            alarm = "disabled" 
            swstate = "disabled"                      
            log.warn "Siren has been turned off using the device's switch"
            online = false
            rememberState("powerSource","unknown")
            lastResponse("Siren has been turned off using the device's switch")   
            break; 
           
		default:
            alarm = "unknown" 
            swstate ="unknown"            
            log.error "Unknown siren state received: ${alarm}"
            online = false
			break;
	    }				
    
    rememberState("alarm",alarm)         
    rememberState("switch",swstate)  
    rememberState("online",online) 
    
    logDebug("setAlarmState(): Alarm(${state.alarm}), Switch(${state.switch})")
}    


def duration(seconds) {
    if (seconds == 65535) {seconds = "Forever"}
    return seconds                              
}

def parse(topic) {     
    processStateData(topic.payload)
}

def void processStateData(payload) {
    rememberState("online","true")       
    
    def object = new JsonSlurper().parseText(payload)    
    def devId = object.deviceId      
    
    if (state.devId == devId) {  // Only handle if message is for me         
        logDebug("processStateData(${payload})")
        
        def child = parent.getChildDevice(state.my_dni)
        def name = child.getLabel()                
        def event = object.event.replace("${state.type}.","")
        logDebug("Received Message Type: ${event} for: $name")
        
        switch(event) {
		case "Report":   
        case "getState":       
            parseDevice(object) 
 		    break;   
            
        case "StatusChange":   
            def alarm = object.data.state
            def volume = object.data.soundLevel    
            def battery = parent.batterylevel(object.data.battery) 
            def powerSource = object.data.powerSupply 
            if (powerSource == "usb") {powerSource = "mains"}
            def alarmDuration = duration(object.data.alarmDuation)   //API message has a spelling error
            def mute = object.data.mute
            def firmware = object.data.version.toUpperCase()
            def rssi = object.data.loraInfo.signal
    
            rememberState("volume",volume)
            rememberState("battery",battery)
            rememberState("powerSource",powerSource)    
            rememberState("alarmDuration",alarmDuration)
            rememberState("mute",mute)
            rememberState("firmware",firmware)    
            fmtSignal(rssi)
    
            setAlarmState(alarm)
 		    break;       
            
        case "setDuation": //API message has a spelling error    
            def alarmDuration = duration(object.data.alarmDuation)   //API message has a spelling error    
            def rssi = object.data.loraInfo.signal
            rememberState("alarmDuration",alarmDuration)
            fmtSignal(rssi)
            break;   
            
        case "setMute":
            def mute = object.data.mute
            def muteDuration = duration(object.data.muteDuration)
            def muteRemaining = duration(object.data.muteRemaining)
    
            rememberState("mute",mute)
            rememberState("muteDuration",muteDuration)
            rememberState("muteRemaining",muteRemaining)
            break;  
            
        case "setTimeZone":
            break;              
            
        case "setState":  //"normal","alert","off"
            def alarm = object.data.state    
            def rssi = object.data.loraInfo.signal
            
            setAlarmState(alarm)
            
            fmtSignal(rssi)
            break; 
           
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }				
    }
}

def reset(){       
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("alarm")
    state.remove("switch")
    state.remove("volume") 
    state.remove("battery")
    state.remove("alarmDuration")
    state.remove("mute")
    state.remove("muteDuration")
    state.remove("muteRemaining")
    state.remove("powerSource")    
    state.remove("firmware")
    state.remove("rssi")
    state.remove("signal")
          
    poll(true)    
    
    log.warn "Device reset to default values"
}

def lastResponse(value) {
   sendEvent(name:"lastResponse", value: "$value", isStateChange:true)   
}

def rememberState(name,value,unit=null) {   
   if (state."$name" != value) {
     state."$name" = value   
     value=value.toString()
     if (unit==null) {  
         sendEvent(name:"$name", value: "$value", isStateChange:true)
     } else {        
         sendEvent(name:"$name", value: "$value", unit: "$unit", isStateChange:true)      
     }           
   }
}   

def successful(object) {
  return (object.code == "000000")     
}    

def notConnected(object) {
  return (object.code == "000201")
}

def pollError(object) {
    def nc = false               //Assume not a connection error
    if (notConnected(object)) {  //Cannot connect to Device
       rememberState("online", "false")                                                                
       log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
       nc = true 
    } else {
       log.error "API polling returned error: $object.code - " + parent.translateCode(object.code)
       lastResponse("Polling error: $object.code - " + parent.translateCode(object.code))         
    }
    
    return nc    
}  

def logDebug(msg) {
  if (state.debug == "true") {log.debug msg}
}

def fmtSignal(rssi) {
   rememberState("rssi",rssi) 
   rememberState("signal",rssi.plus(" dBm")) 
}    