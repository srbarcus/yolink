/***
 *  YoLink™ Valve (YS4909-UC)
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
 *         - Corrected attribute types
 *  1.0.3: def temperatureScale()
 *  1.0.4: Fix donation URL
 *  1.0.5: Added getSetup()
 *  2.0.0: Reengineer driver to use centralized MQTT listener due to new YoLink service restrictions
 *  2.0.1: Define as SingleThreaded
 *         - Removed delay_ch attribute - no useful since device on has a single channel
           - Added delay_on attribute
           - Change null delay_on or delay_off value to 0
 *  2.0.2: Handle "Alert" event
 *  2.0.3: Added formatted "signal" attribute as rssi & " dBm"
 *         - Added capability "SignalStrength" 
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.3"}
def copyright() {return "<br>© 2022, 2023 Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "YoLink™ Valve (YS4909-UC) v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink Manipulator Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"	
        capability "Valve"
        capability "Battery"
        capability "SignalStrength"
                             
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]] 
        command "reset"

        command "open"                         
        command "close"  
        
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"  
        attribute "signal", "String"
        attribute "lastResponse", "String" 
  
        attribute "delay_on", "Number"     
        attribute "delay_off", "Number"  
        attribute "openRemind", "Number"        
        attribute "time", "String"
        attribute "tzone", "String"        
        attribute "schedules", "Number"
        attribute "schedule1", "String"
        attribute "schedule2", "String"
        attribute "schedule3", "String"
        attribute "schedule4", "String"
        attribute "schedule5", "String"
        attribute "schedule6", "String"       
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
    
    logDebug("Last Poll:${lastPoll} Current Time:${cur_time} Run at:${min_time}")

    if (cur_time < min_time ) {
       log.warn "Polling interval of once every ${min_seconds} seconds exceeded, device was not polled."	
    } else { 
       logDebug("Getting device state")  
       runIn(1,getDevicestate)           
       state.lastPoll = now() 
    }      
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

def open () {
   setValve("open")   
}
    
def close () {
   setValve("close")  
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
                rememberState("online", "true") 
                lastResponse("Success") 
            } else {  //Error
               if (pollError(object) ) {  //Cannot connect to Device
                 sendEvent(name:"valve", value: "unknown", isStateChange:true)
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
   def valve = object.data.state
   def battery = parent.batterylevel(object.data.battery)    
   def delay_on = object.data.delay.on  
   def delay_off = object.data.delay.off    
   def openRemind = object.data.openRemind   
   def firmware = object.data.version.toUpperCase()
   def time = object.data.time
   def tzone = object.data.tz
   def rssi = object.data.loraInfo.signal      
   
   if (delay_on == null) {delay_on = 0}
   if (delay_off == null) {delay_off = 0}
                
   rememberState("online", "true")
   rememberState("valve", valve)    
   rememberState("battery", battery) 
   rememberState("delay_on", delay_on) 
   rememberState("delay_off", delay_off)
   rememberState("openRemind", openRemind)   
   rememberState("firmware", firmware)
   rememberState("time", time)
   rememberState("tzone", tzone)
   fmtSignal(rssi)                         
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
        case "Alert":     
            def valve = object.data.state
            def rssi = object.data.loraInfo.signal             
    
            logDebug("Parsed: Valve=$valve, RSSI=$rssi")
            
            rememberState("valve", valve)    
            fmtSignal(rssi)                          
		    break;
            
		case "setDelay":            
            def delay_on = object.data.delay.on   
            def delay_off = object.data.delay.off    
            
            if (delay_on == null) {delay_on = 0}
            if (delay_off == null) {delay_off = 0}
       
            logDebug("Parsed: Delay_on=$delay_on, Delay_off=$delay_off")      
                
            rememberState("delay_on", delay_on)               
            rememberState("delay_off", delay_off)               
			break;
            
		case "setTimeZone":        
            def tzone = object.data.tz
            logDebug("Parsed: Timezone=$tzone")
            rememberState("tzone",tzone)                          
			break;
            
        case "setState":
            def valve = object.data.state   
            def rssi = object.data.loraInfo.signal             
    
            logDebug("Parsed: Valve=$valve, RSSI=$rssi")
            
            rememberState("valve", valve)    
            fmtSignal(rssi)                                       
			break;  
            
        case "getState":           
		case "Report":
            parseDevice(object)
			break;	
            
        case "setSchedules":
            def schedules = object.data
                
            logDebug("Parsed: Schedules=$schedules")
            
            state.remove("schedules")
            state.remove("schedule1")
            state.remove("schedule2")
            state.remove("schedule3")
            state.remove("schedule4")
            state.remove("schedule5")
            state.remove("schedule6")
            
            def schedNum = 0
            def scheds = 0
            while (schedNum < 6){        
                if (object.data."${schedNum}" != null) { 
                  def schedule  =  object.data."${schedNum}".toString() 
                  
                  def weekhex =  object.data."${schedNum}".week 
                    
                  def weekdays = parent.scheduledDays(weekhex)                    
                                                   
                  schedule = schedule.replaceAll(" week=${weekhex},"," days=[${weekdays}],")                                          
                  schedule = schedule.replaceAll(" index=${schedNum},","")                       
                  schedule = schedule.replaceAll("isValid=","enabled=") 
                  schedule = schedule.replaceAll("=25:0","=never")   
                  
                  scheds++  
                  log.trace "Schedule ${scheds}: ${schedule}"
                  rememberState("schedule${scheds}",schedule)
                } 
                schedNum++
            }  
            
            rememberState("schedules",scheds)
            
            scheds++  
            while (scheds <= 6){        
                sendEvent(name:"schedule${scheds}", value: " ", isStateChange:false)
                scheds++
            }              
           
			break; 
                
        case "getSchedules":    //Old schedule
            break;  

        case "setOpenRemind":            
            def openRemind = object.data.openRemind  
            rememberState("openRemind", openRemind)   
            break;  
            
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }      
}

def setValve(setState) {
   def params = [:] 
   params.put("state", setState)    
    
   def request = [:] 
   request.put("method", "${state.type}.setState")      
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
      def object = parent.pollAPI(request, state.name, state.type)  
      def valve = object.data.state
      def rssi = object.data.loraInfo.signal         
  
      rememberState("valve", valve)    
      state.remove("signal")   
   
    } catch (e) {	
        log.error "setValve() exception: $e"
        lastResponse("Error ${e}")     
        sendEvent(name:"valve", value: "unknown", isStateChange:true)
	} 
}   

def reset(){
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("LastResponse") 
    state.remove("firmware") 
    state.remove("battery")
    state.remove("delay_on")
    state.remove("delay_off")
    state.remove("openRemind")   
    state.remove("time")  
    state.remove("tzone")  
    state.remove("rssi") 
    state.remove("signal")    
    state.remove("schedules") 
    state.remove("schedule1")
    state.remove("schedule2")
    state.remove("schedule3")
    state.remove("schedule4")
    state.remove("schedule5")
    state.remove("schedule6")    
 
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