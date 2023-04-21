/***
 *  YoLink™ Fob (YS3604-UC)
 *  © 2022, 2023 Steven Barcus. All rights reserved.
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
 *  1.0.1: Fixed errors in poll()
 *  1.0.2: Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 *  1.0.3: Fix syncing of Temperature scale with YoLink™ Device Service app
 *  1.0.4: Fix donation URL
 *  1.0.5: Added getSetup()
 *  2.0.0: Reengineer driver to use centralized MQTT listener due to new YoLink service restrictions 
 *  2.0.1: - Fix problem with multiple actions on same button being ignored: Added Double-Tap and Tap Delay attributes
 *         - Clean up code
 *         - Remove temperature as it never changed
 *  2.0.2: Support diagnostics, correct various errors, make singleThreaded
 *  2.0.3: Support event "SmartRemoter.Report"
 *         - Add "DoubleTapableButton" capability
 *         - Add unit value to battery 
 *         - Add formatted "signal" attribute as rssi & " dBm"
 *         - Add capability "SignalStrength"  
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.3"}
def copyright() {return "<br>© 2022, 2023 Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "YoLink™ Fob (YS3604-UC) v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink SmartRemoter Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {
        capability "Polling"	
        capability "Battery"
        capability "HoldableButton"
        capability "PushableButton"
        capability "DoubleTapableButton"
        capability "SignalStrength"             //rssi         
        
        command "allowDoubleTap", [[name:"Enable double-tapping",type:"ENUM", description:"Allow pressing or holding of the same button without using a different button first)", constraints:[true, false]]]  
        command "tapDelay", [[name:"Maximum seconds between presses for a double-tap",type:"ENUM", description:"Maximum number of seconds between pressing of the same button to be considered as a double-tap if double-tapping is enabled.", constraints:[0, 0.5, 1, 2, 5]]]  
        command "debug", [[name:"Enable debugging",type:"ENUM", description:"Display debugging messages", constraints:[true, false]]] 
        command "reset"            
                 
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"  
        attribute "signal", "String" 
        attribute "lastResponse", "String" 
        attribute "remoteType", "String"         
        attribute "reportAt", "String"
        attribute "tapDelay", "String" 
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
    rememberState("numberOfButtons",4)         
    rememberState("remoteType","FlexFob")  
 }

def updated() {
    log.info "Device Updated" 
    rememberState("driver", clientVersion()) 
    log.info "Driver updated - reseting device"
    reset()
 }

def uninstalled() {
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def poll(force=null) {
    logDebug("poll(${force})")
    
    rememberState("driver", clientVersion())

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
       logDebug("Getting device state")
       runIn(1,getDevicestate)
       state.lastPoll = now()
    }  
 }


def tapDelay(value) {    
   logDebug("tapDelay(${value})")  
   rememberState("tapDelay",value)                
 }

def allowDoubleTap(value) {    
   logDebug("allowDoubleTap(${value})")  
   rememberState("allowDoubleTap",value)                
 }

def doubleTap(button) {    
   logDebug("doubleTap(${button})")   
   rememberState("doubleTapped",button,null,true)         
   rememberState("action","doubleTapped",null,true)
               
 }

def push(button) {    
   if (!honorTap(button)) {
     logDebug("Pushed(${button})")
     rememberState("pushed",button,null,true)              
     rememberState("action","pushed",null,true)
   }           
 }

def hold(button) {   
   logDebug("Held(${button})")
   rememberState("held",button,null,true)           
   rememberState("action","held",null,true)
 }

def connect() {
    establish_MQTT_connection(state.my_dni)
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

def getDevicestate() {
    state.driver=clientVersion()
    
	logDebug("getDevicestate() obtaining device state")
    
	boolean rc=false	//DEFAULT: Return Code = false   
    
	try {  
        def request = [:]
            request.put("method", "${state.type}.getState")                 
            request.put("targetDevice", "${state.devId}") 
            request.put("token", "${state.token}") 
        
        def object = parent.pollAPI(request,state.name,state.type)
   
        if (object) {
            logDebug("getDevicestate()> pollAPI() response: ${object}")     
            
            if (successful(object)) {                
                parseDevice(object)                     
                rc = true	
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
   def devId = object.data.deviceId  
   def reportAt = object.data.reportAt
    
   def online = object.data.online
    
   def battery = parent.batterylevel(object.data.state.battery)            
   def firmware = object.data.state.version.toUpperCase()   
    
   logDebug("Parsed: DeviceId=$devId, Battery=$battery, Report At=$reportAt, Firmware=$firmware, Online=$online")   
                
   rememberState("online", "true")
   rememberState("battery", battery, "%")
   rememberState("reportAt", reportAt)  
   rememberState("firmware", firmware)   
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
		case "StatusChange":
        case "Report":    
            def button = object.data.event.keyMask 
            def action = object.data.event.type 
            def battery = parent.batterylevel(object.data.battery)
            def firmware = object.data.version.toUpperCase() 
          //def temperature = object.data.devTemperature     // Never changes on FlexFob YS3604 V1
            def rssi = object.data.loraInfo.signal  
    
            logDebug("Parsed: DeviceId=$devId, Button=$button, Action=$action, Battery=$battery, Firmware=$firmware, RSSI=$rssi")
            
            switch(button) {
		        case "4":                      
                    button = 3                    
                    break;
                case "8":          
                    button = 4                    
                    break;                  
	        }
            
            switch(action) {
                case "Press":   
                    push(button)
                    break;
                case "LongPress":    
                    hold(button)
                    break;                  
	        }
            
            rememberState("battery", battery, "%")
            rememberState("firmware",firmware)  
            fmtSignal(rssi) 
		    break;           
		                
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }      
}

def honorTap(button) {
    def rc = false
    if (button == state.lastButton) {
      def secsPassed = ((now()/1000) - (state.lastTap/1000))
      def secsDelay = state.tapDelay
      logDebug("Seconds between last press = $secsPassed, Delay=$secsDelay") 
      if (((secsPassed.toBigDecimal() < secsDelay.toBigDecimal()) || (secsDelay==0)) && (state.allowDoubleTap == "true")) {
         doubleTap(button) 
         rc = true
      }
    }
    
    state.lastTap = now() 
    state.lastButton = button
    return rc
}   

def reset(){          
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("firmware")
    state.remove("rssi")
    state.remove("signal")
    state.remove("battery")  
    state.remove("doubleTap")
    state.remove("pushed")
    state.remove("held")
    state.remove("tapDelay")      
    
    allowDoubleTap("true")
    rememberState("tapDelay",1)
        
    state.lastTap = now()
    state.lastButton = 0
        
    poll(true)    
        
    log.warn "Device reset to default values"
}

def lastResponse(value) {
   sendEvent(name:"lastResponse", value: "$value", isStateChange:true)   
}

def rememberState(name, value, unit=null, force=false) {
   if ((state."$name" != value) || (force=="true")) {
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
  return (object.code  == "000000")     
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