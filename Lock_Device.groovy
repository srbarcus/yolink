/***
 *  YoLink™ Lock Device (YS7606-UC,YUF-02BN)
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
 *  2.0.1: Added "driver" attribute and isSetup() for diagnostics
 *         - Define as singleThreaded
 *         - Added "PushableButton" capability for doorbell
 *  2.0.2: Added formatted "signal" attribute as rssi & " dBm"
 *         - Added capability "SignalStrength" 
 *  2.0.3: Prevent Service app from waiting on device polling completion
 *         - Add "%" unit to 'battery'
 *  2.0.4: Updated driver version on poll
 *  2.0.5: Support "setDeviceToken()"
 *         - Update copyright
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.5"}
def copyright() {return "<br>© 2022-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "YoLink™ Lock Device (YS7606-UC,YUF-02BN) v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Date Format Template Specifications"), description: "<p>Click the link to view the possible letters used in timestamp formatting template. <a href=\"https://github.com/srbarcus/yolink/blob/main/DateFormats.txt\">Date Format Template Characters</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"    
}

metadata {
    definition (name: "YoLink Lock Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     
		capability "Polling"        
        capability "Lock"
        capability "Battery"
        capability "PushableButton"
        capability "SignalStrength"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]]
        command "reset"    
        command "push"    
        command "timestampFormat", [[name:"timestampFormat",type:"STRING", description:"Formatting template for event timestamp values. See Preferences below for details."]] 
    
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"  
        attribute "signal", "String"
        attribute "lastPoll", "String"
        attribute "lastResponse", "String"  
        attribute "Type", "String"
        attribute "source", "String"
        attribute "user", "String"        
        attribute "doorbell", "String"
        attribute "pushed", "String"
        attribute "passwordError", "String"
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
    rememberState("passwordError", "false")
    def date = new Date()
    sendEvent(name:"lastPoll", value: date.format("MM/dd/yyyy hh:mm:ss a"), isStateChange:true)
 }

def temperatureScale(value) {}

def timestampFormat(value) {
    value = value ?: "MM/dd/yyyy hh:mm:ss a" // No value, reset to default
    def oldvalue = state.timestampFormat 
    
    //Validate requested value
    try{                           
       def date = new Date()  
       def stamp = date.format(value)   
       state.timestampFormat = value   
       logDebug("Date format set to '${value}'")
       logDebug("Current date and time in requested format: '${stamp}'")  
     } catch(Exception e) {       
       //log.error "dateFormat() exception: ${e}"
       log.error "Requested date format, '${value}', is invalid. Format remains '${oldvalue}'" 
     } 
 }

def debug(value) { 
   rememberState("debug",value)
   if (value=="true") {
     log.info "Debugging enabled"
   } else {
     log.info "Debugging disabled"
   }    
}

def lock () {
   if (state.lock == "locked") { 
       lastResponse("Lock is already locked, request ignored")            
   } else {       
       def lock = setLock("lock")
       if (lock == "locked") {
         logDebug("Lock is now locked")
         lastResponse("Lock is now locked")   
       } else {
         log.error "Error locking the lock"
         lastResponse("Error locking the lock")     
       }    
   }         
}

def push() {
   logDebug("Door bell rung")     
   sendEvent(name:"doorbell", value: "true", isStateChange:true)                
   sendEvent(name:"pushed", value: "1", isStateChange:true)
   runIn(5,resetDoorbell) 
}

def resetDoorbell() {
   logDebug("Door bell reset")     
   sendEvent(name:"doorbell", value: "false", isStateChange:true)                
}

def unlock () {
  if (state.lock == "unlocked") { 
       lastResponse("Lock is already unlocked, request ignored")            
   } else {         
       def lock = setLock("unlock")
       if (lock == "unlocked") {
         logDebug("Lock is now unlocked")
         lastResponse("Lock is now unlocked")     
       } else {
         log.error "Error unlocking the lock"
         lastResponse("Error unlocking the lock")     
       }    
   }      
}      

def getDevicestate() {
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
                    
                logDebug("Fetching device state")    
    
	            try {  
                    request = [:]
                    request.put("method", "${state.type}.fetchState") 
                    request.put("targetDevice", "${state.devId}") 
                    request.put("token", "${state.token}") 
        
                    object = parent.pollAPI(request,state.name,state.type)
              
                    if (object) {
                        logDebug("getDevicestate() fetch> pollAPI() response: ${object}")              
            
                        if (successful(object)) {                
                            parseFetch(object)                     
                            rc = true	                            
                            lastResponse("Success")              
                
                        } else {  //Error
                           if (pollError(object) ) {  //Cannot connect to Device
                             rememberState("lock", "unknown")                      
                           }
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
            } else {  //Error
               if (pollError(object) ) {  //Cannot connect to Device
                 rememberState("lock", "unknown")                      
               }
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
   def lock = object.data.state
   def battery = object.data.battery 
   def lockset = object.data.rlSet   
   def rssi = object.data.loraInfo.signal     
    
   battery = parent.batterylevel(battery) 
     
   logDebug("Parsed: Lock=$lock, Battery=$battery, Lockset=$lockset, RSSI=$rssi")                      

   rememberState("lock", lock)
   rememberState("battery", battery, "%")
   rememberState("lockset", lockset) 
   fmtSignal(rssi)   
}   

def parseFetch(object) {
   logDebug("parseFetch(${object})") 
   def online = object.data.online 
   def battery = object.data.state.battery        
   def lockset = object.data.state.rlSet   
   def lock = object.data.state.state 
   def timezone = object.data.state.tz  
   def firmware = object.data.state.version.toUpperCase()
   def source = object.data.state.source
   def alertType  = object.data.state.alertType
   def user = object.data.state.user    
       
   battery = parent.batterylevel(battery) 
    
   if (source == "app") {
     user = "app" 
   }
     
   logDebug("Parsed: Online=$online, Battery=$battery, Lockset=$lockset, Lock=$lock, Timezone=$timezone, Firmware=$firmware, Source=$source, Alert Type=$alertType, User=$user")      
                
   rememberState("online", online)
   rememberState("battery", battery, "%") 
   rememberState("lockset", lockset)  
   rememberState("lock", lock)
   rememberState("timezone", timezone) 
   rememberState("firmware", firmware) 
   rememberState("source", source)  
   rememberState("alertType", alertType)  
   rememberState("user", user)   
}  

def parse(topic) {     
    logDebug("parse($topic)")    
    processStateData(topic.payload)
}

def void processStateData(payload) {
    rememberState("online","true") 
    rememberState("passwordError", "false") 
                   
    def object = new JsonSlurper().parseText(payload)    
    
    def time = formatTimestamp(object.time)    
    rememberState("eventTime",time)
       
    def devId = object.deviceId   
            
    if (state.devId == devId) {  // Only handle if message is for me 
        logDebug("processStateData(${payload})")
        
        def child = parent.getChildDevice(state.my_dni)
        def name = child.getLabel()                
        def event = object.event.replace("${state.type}.","")
        logDebug("Received Message Type: ${event} for: $name")
        
        switch(event) {
        case "Report":    
		case "StatusChange":
        case "getState":     
            parseDevice(object)
		    break;
            
        case "addTemporaryPWD":     
            lastResponse("Temporary guest password defined") 
            log.warn "A temporary guest password was defined"
			break;    
            
        case "addPassword":     
            def user = object.data.index + 1
            lastResponse("Password defined for user ${user}") 
			break;     
          
        case "updatePassword":     
            def user = object.data.index + 1
            lastResponse("Password updated for user ${user}") 
			break;     
     
        case "delPassword":     
            def user = object.data.index + 1
            lastResponse("Password deleted for user ${user}") 
			break;     
            
        case "setState":
            def lock = parent.relayState(object.data.state)   
            def rssi = object.data.loraInfo.signal   
            def source = object.data.source
    
            logDebug("Parsed: DeviceId=$devId, Lock=$lock, RSSI=$rssi, Source=$source")
            
            rememberState("lock",lock)
            fmtSignal(rssi)                                       
            rememberState("source",source)  
			break;  
            
        case "Alert":
            def lock = object.data.state
            def battery = object.data.battery        
            def alertType  = object.data.alertType
            def source = object.data.source   
            def user = object.data.user   
            def rssi = object.data.loraInfo.signal            
       
            battery = parent.batterylevel(battery) 
     
            logDebug("Parsed: Battery=$battery, Lock=$lock, Source=$source, Alert Type=$alertType, User=$user, RSSI=$rssi")      
            
            if ((source == null) && (user == null) && (alertType=="bell")) {
                push()
            } else {    
                rememberState("source", source) 
                rememberState("user", user)   
            }    
            
            if (alertType == "pwderror") {
                log.warn "Too mant password attempts"     
                sendEvent(name:"passwordError", value: "true", isStateChange:true)                
            }    
                
            rememberState("online", "true")
            rememberState("battery", battery, "%") 
            rememberState("lock", lock)               
            fmtSignal(rssi)                                      
			break;      
            

        case "getUsers":
			break;                        

            
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }      
}

def setLock(setState) {
   def params = [:] 
   params.put("state", setState)    
    
   def request = [:] 
   request.put("method", "${state.type}.setState")                  
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
        def lock
         
        if (object) {
            logDebug("setLock(): pollAPI() response: ${object}")  
            
            if (successful(object)) {     
                def time = formatTimestamp(object.time) 
                rememberState("eventTime",time)
                
                lock = object.data.state   
                def rssi = object.data.loraInfo.signal       
                def source = object.data.source
                logDebug("Parsed: Lock=$lock, RSSI=$rssi, Source=$source")
                rememberState("lock",lock)
                fmtSignal(rssi)  
                rememberState("source",source)   
                rememberState("user", "app")  
                lastResponse("Lock ${lock}")     
                               
            } else {                
                if (notConnected(object)) { 
                   getDevicestate()  
                   lock = state.lock 
                   lastResponse("Lock ${lock}")      
                } else {
                   rememberState("lock","unknown")  
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
                   lock = "unknown" 
                }
            }                     
                                        
            return lock							
                
	    } else { 			               
            logDebug("setSwitch() failed")	
            state.lock = "unknown"
            sendEvent(name:"switch", value: state.lock, isStateChange:true)
            lastResponse("setSwitch() failed")     
        }     		
	} catch (e) {	
        log.error "setSwitch() exception: $e"
        lastResponse("Error ${e}")     
        state.lock = "unknown"
        sendEvent(name:"switch", value: state.lock, isStateChange:true)  
	} 
}  

def formatTimestamp(timestamp){    
    if ((state.timestampFormat != null) && (timestamp != null)) {
      def date = new Date( timestamp as long )    
      date = date.format(state.timestampFormat)
      logDebug("formatTimestamp(): '$state.timestampFormat' = '$date'")
      return date  
    } else {
      return timestamp  
    }    
}

def reset(){
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("lock")
    state.remove("eventTime")  
    state.remove("rssi")
    state.remove("signal")  
    state.remove("firmware") 
    state.remove("LastResponse")  
    state.remove("doorbell")
    state.remove("source")
    state.remove("user")
    state.remove("battery")
    state.remove("passwordError")

    state.timestampFormat = "MM/dd/yyyy hh:mm:ss a"     
    rememberState("lock", "unknown")

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