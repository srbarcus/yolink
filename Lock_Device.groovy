/***
 *  YoLink™ Lock Device (YS7606-UC,YUF-02BN)
 *  © 2022 Steven Barcus
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
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 *  
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.0"}

preferences {
    input title: "Driver Version", description: "YoLink™ Lock Device (YS7606-UC,YUF-02BN) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Please donate", description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Date Format Template Specifications", description: "<p>Click the link to view the possible letters used in timestamp formatting template. <a href=\"https://github.com/srbarcus/yolink/blob/main/DateFormats.txt\">Date Format Template Characters</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"    
}

metadata {
    definition (name: "YoLink Lock Device", namespace: "srbarcus", author: "Steven Barcus") {     
		capability "Polling"        
        capability "Lock"
        capability "Battery"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]]
        command "reset"        
        command "timestampFormat", [[name:"timestampFormat",type:"STRING", description:"Formatting template for event timestamp values. See Preferences below for details."]] 
    
        attribute "online", "String"
        attribute "firmware", "String"  
        attribute "signal", "String"
        attribute "lastResponse", "String"  
        attribute "Type", "String"
        attribute "source", "String"
        attribute "user", "String"        
        attribute "doorbell", "String"
        attribute "passwordError", "String"
        }
 }

void ServiceSetup(Hubitat_dni,homeID,devname,devtype,devtoken,devId) {  
    state.debug = false
    
    state.my_dni = Hubitat_dni      
    state.homeID = homeID    
    state.name = devname
    state.type = devtype
    state.token = devtoken
    state.devId = devId   
    
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

def installed() {
 }

def updated() {
 }

def uninstalled() {
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def poll(force=null) {
    if (force == null) {
      def min_interval = 10                  // To avoid unecessary load on YoLink servers, limit rate of polling
	  def min_time = (now()-(min_interval * 1000))
	  if ((state?.lastPoll) && (state?.lastPoll > min_time)) {
         log.warn "Polling interval of once every ${min_interval} seconds exceeded, device was not polled."	    
         return     
       } 
    }    
    
    rememberState("doorbell", "false") 
    rememberState("passwordError", "false") 
    
    getDevicestate() 
    state.lastPoll = now()    
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
   if (value) {
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
   def signal = object.data.loraInfo.signal     
    
   battery = parent.batterylevel(battery) 
     
   logDebug("Parsed: Lock=$lock, Battery=$battery, Lockset=$lockset, Signal=$signal")                      

   rememberState("lock", lock)
   rememberState("battery", battery)
   rememberState("lockset", lockset) 
   rememberState("signal", signal)   
}   

def parseFetch(object) {
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
   rememberState("battery", battery) 
   rememberState("lockset", lockset)  
   rememberState("lock", lock)
   rememberState("timezone", timezone) 
   rememberState("firmware", Firmware) 
   rememberState("source", source)    
   rememberState("user", user)   
}  


def parse(topic) {     
    logDebug("parse($topic)")    
    processStateData(topic.payload)
}

def void processStateData(payload) {
    rememberState("online","true") 
    rememberState("doorbell", "false") 
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
            def signal = object.data.loraInfo.signal   
            def source = object.data.source
    
            logDebug("Parsed: DeviceId=$devId, Lock=$lock, Signal=$signal, Source=$source")
            
            rememberState("lock",lock)
            rememberState("signal",signal)                                       
            rememberState("source",source)  
			break;  
            
        case "Alert":
            def lock = object.data.state
            def battery = object.data.battery        
            def alertType  = object.data.alertType
            def source = object.data.source   
            def user = object.data.user   
            def signal = object.data.loraInfo.signal            
       
            battery = parent.batterylevel(battery) 
     
            logDebug("Parsed: Battery=$battery, Lock=$lock, Source=$source, Alert Type=$alertType, User=$user, Signal=$signal")      
            
            if ((source == null) && (user == null) && (alertType=="bell")) {
                logDebug("Door bell rung")     
                sendEvent(name:"doorbell", value: "true", isStateChange:true)                
            } else {    
                rememberState("source", source) 
                rememberState("user", user)   
            }    
            
            if (alertType == "pwderror") {
                log.warn "Too mant password attempts"     
                sendEvent(name:"passwordError", value: "true", isStateChange:true)                
            }    
                
            rememberState("online", "true")
            rememberState("battery", battery) 
            rememberState("lock", lock)               
            rememberState("signal",signal)                                       
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
                def signal = object.data.loraInfo.signal       
                def source = object.data.source
                logDebug("Parsed: Lock=$lock, Signal=$signal, Source=$source")
                rememberState("lock",lock)
                rememberState("signal",signal)  
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
    state.debug = false
    state.remove("lock")
    state.remove("eventTime")  
    state.remove("signal")        
    state.remove("online")  
    state.remove("LastResponse")  
    state.remove("doorbell")
    state.remove("source")
    state.remove("user")
    state.remove("battery")
    state.remove("passwordError")

    state.timestampFormat = "MM/dd/yyyy hh:mm:ss a"     
    rememberState("lock", "unknown")

    poll(true)
   
    logDebug("Device reset to default values")
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
   if (state.debug) {log.debug msg}
}