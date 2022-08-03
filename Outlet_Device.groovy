/***
 *  YoLink™ Plug (YS6604-UC) and In-wall outlet (YS6704-UC)
 *  © 2022 Steven Barcus
 *  THIS SOFTWARE IS NEITHER DEVELOPED, ENDORSED, OR ASSOCIATED WITH YoLink™ OR YoSmart, Inc.
 *   
 *  DO NOT INSTALL THIS DEVICE MANUALLY - IT WILL NOT WORK. MUST BE INSTALLED USING THE YOLINK DEVICE SERVICE APP  
 *
 *  Donations are appreciated and allow me to purchase more YoLink devices for development: https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1&currency_code=USD 
 *   
 *  Developer retains all rights, title, copyright, and interest, including patent rights and trade
 *  secrets in this software. Developer grants a non-exclusive perpetual license (License) to User to use
 *  this software under this Agreement. However, the User shall make no commercial use of the software without
 *  the Developer's written consent. Software Distribution is restricted and shall be done only with Developer's written approval.
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * 
 *  1.0.1: Fixed errors in poll()
 *  1.0.2: (skipped)
 *  1.0.3: Fix clientVersion()
 *  1.0.4: Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 *         - Removed delay channel processing - always 1 (only one plug)
 *  1.0.5: Verified support for In-wall outlet (YS6704-UC)
 *  1.0.6: Added "Switch" capability
 */

import groovy.json.JsonSlurper

def clientVersion() {return "1.0.6"}

preferences {
    input title: "Driver Version", description: "YoLink™ Plug (YS6604-UC) or In-wall outlet (YS6704-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
	input title: "Please donate", description: "Donations allow me to purchase more YoLink devices for development. Copy and Paste the following into your browser: https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink Outlet Device", namespace: "srbarcus", author: "Steven Barcus") {     	
		capability "Polling"	
        capability "RelaySwitch"
        capability "Switch"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]]
        command "connect"                       // Attempt to establish MQTT connection
        command "reset"
        command "announce", ['String'] 
        
        command "on"                         
        command "off"  
        
        attribute "API", "String" 
        attribute "online", "String"
        attribute "firmware", "String"  
        attribute "signal", "String"
        attribute "lastResponse", "String" 
        
        attribute "delay_on", "Number"  
        attribute "delay_off", "Number"  
        attribute "time", "String"
        attribute "tzone", "String"        
        attribute "powerOnState", "String"  
        attribute "schedules", "integer"
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
    state.devId = devId   
    
	log.info "ServiceSetup(Hubitat dni=${state.my_dni}, Home ID=${state.homeID}, Name=${state.name}, Type=${state.type}, Token=${state.token}, Device Id=${state.devId})"	 
    
    reset()      
 }

def installed() {
 }

def updated() {
 }

def uninstalled() {
   interfaces.mqtt.disconnect() // Guarantee we're disconnected  
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
    
    getDevicestate() 
    check_MQTT_Connection()
    state.lastPoll = now()    
 }

def connect() {
    establish_MQTT_connection(state.my_dni)
 }

def debug(value) { 
    def bool = parent.validBoolean("debug",value)
    
    if (bool != null) {
        if (bool) {
            state.debug = true
            log.info "Debugging enabled"
        } else {
            state.debug = false
            log.info "Debugging disabled"
        }   
    }        
}

def on () {
   def swState = setSwitch("open")
   if (swState == "on") {
     logDebug("Switch turned on")
   } else {
     log.error "Error turning switch on"
   }    
}
    
def off () {
  def swState = setSwitch("close")
  if (swState == "off") {
    logDebug("Switch turned off")
  } else {
    log.error "Error turning switch off"
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
                rememberState("online", "true") 
                lastResponse("Success") 
            } else {  //Error
               if (pollError(object) ) {  //Cannot connect to Device
                 rememberState("switch", "unknown")                      
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
   def devId = object.deviceId  
   def swState = parent.relayState(object.data.state)   
   def delay_on = object.data.delay.on
   def delay_off = object.data.delay.off    
   def power = object.data.power 
   def watt = object.data.watt 
   def firmware = object.data.version
   def time = object.data.time
   def tzone = object.data.tz
   def signal = object.data.loraInfo.signal         
    
   logDebug("Parsed: DeviceId=$devId, Switch=$swState, Delay_on=$delay_on, Delay_off=$delay_off, Power=$power, Time=$time, Timezone=$tzone, Firmware=$firmware, Signal=$signal")      
                
   rememberState("online", "true")
   rememberState("switch", swState)
   rememberState("delay_on", delay_on)
   rememberState("delay_off", delay_off)
   rememberState("power", power)      
   rememberState("firmware", firmware)
   rememberState("time", time)
   rememberState("tzone", tzone)
   rememberState("signal", signal)                         
}   

def check_MQTT_Connection() {
  def MQTT = interfaces.mqtt.isConnected()  
  logDebug("MQTT connection is ${MQTT}")  
  if (MQTT) {  
     rememberState("API", "connected")     
  } else {    
     establish_MQTT_connection(state.my_dni)      //Establish MQTT connection to YoLink API
  }
}    

def establish_MQTT_connection(mqtt_ID) {
    parent.refreshAuthToken()
    def authToken = parent.AuthToken() 
      
    def MQTT = "disconnected"
    
    def topic = "yl-home/${state.homeID}/${state.devId}/report"
    
    try {  	
        mqtt_ID =  "${mqtt_ID}_${state.homeID}"
        logDebug("Connecting to MQTT with ID '${mqtt_ID}', Topic:'${topic}, Token:'${authToken}")
      
        interfaces.mqtt.connect("tcp://api.yosmart.com:8003","${mqtt_ID}",authToken,null)                         	
          
        logDebug("Subscribing to MQTT topic '${topic}'")
        interfaces.mqtt.subscribe("${topic}", 0) 
         
        MQTT = "connected"          
          
        logDebug("MQTT connection to YoLink successful")
		
	} catch (e) {	
        log.error ("establish_MQTT_connection() Exception: $e")	
    }
     
    rememberState("API", MQTT)    
    lastResponse("API MQTT ${MQTT}")    
}    

def mqttClientStatus(String message) {                          
    logDebug("mqttClientStatus(${message})")

    if (message.startsWith("Error:")) {
        log.error "MQTT Error: ${message}"

        try {
            log.warn "Disconnecting from MQTT"    
            interfaces.mqtt.disconnect()           // Guarantee we're disconnected            
            rememberState("API","disconnected") 
        }
        catch (e) {
        } 
    }
}

def parse(message) { 
    def topic = interfaces.mqtt.parseMessage(message)
    def payload = new JsonSlurper().parseText(topic.payload)
    logDebug("parse(${payload})")

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
            def swState = parent.relayState(object.data.state)            
            def signal = object.data.loraInfo.signal             
    
            logDebug("Parsed: DeviceId=$devId, Switch=$swState, Signal=$signal")
            
            rememberState("switch",swState)
            rememberState("signal",signal)                              
		    break;
            
         
        case "setDelay":     
            def delay_on = object.data.delayOn
            def delay_off = object.data.delayOff    
   
            rememberState("delay_on", delay_on)
            rememberState("delay_off", delay_off)                
			break;    
            
		case "setInitState":
            def powerOnState = parent.relayState(object.data.initState) 
            
            rememberState("powerOnState",powerOnState)                 
			break;
            
		case "setTimeZone":
            def tzone = object.data.tz
            logDebug("Parsed: Timezone=$tzone")
            rememberState("tzone",tzone)                          
			break;
            
        case "setState":
            def swState = parent.relayState(object.data.state)   
            def signal = object.data.loraInfo.signal             
    
            logDebug("Parsed: DeviceId=$devId, Switch=$swState, Signal=$signal")
            
            rememberState("switch",swState)
            rememberState("signal",signal)                                       
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
                    
                  log.trace "Days of week: ${weekdays}"    
                  
                  log.trace "schedule ${schedule}"                        
                                                   
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
            
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }      
}

def setSwitch(setState) {
   def params = [:] 
   params.put("state", setState)    
    
   def request = [:] 
   request.put("method", "${state.type}.setState")                  
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
        def swState
         
        if (object) {
            logDebug("setSwitch(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {               
                swState = parent.relayState(object.data.state)   
                def signal = object.data.loraInfo.signal       
                logDebug("Parsed: Switch=$swState, Signal=$signal")
                rememberState("switch",swState)
                rememberState("signal",signal)  
                lastResponse("Switch ${swState}")     
                               
            } else {                
                if (notConnected(object)) { //Cannot connect to Device - YoLink bug? Device appears offline while state is changing
                   getDevicestate()  
                   swState = state.switch 
                   lastResponse("Switch ${swState}")      
                } else {
                   rememberState("switch","unknown")  
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
                   swState = "unknown" 
                }
            }                     
                                        
            return swState							
                
	    } else { 			               
            logDebug("setSwitch() failed")	
            state.swState = "unknown"
            sendEvent(name:"switch", value: state.swState, isStateChange:true)
            lastResponse("setSwitch() failed")     
        }     		
	} catch (e) {	
        log.error "setSwitch() exception: $e"
        lastResponse("Error ${e}")     
        state.swState = "unknown"
        sendEvent(name:"switch", value: state.swState, isStateChange:true)  
	} 
}  

def reset(){          
    state.debug = false
    state.remove("API")
    state.remove("firmware")
    state.remove("switch")
    state.remove("delay_ch")        //Note: remove in future
    state.remove("delay_on")
    state.remove("delay_off")    
    state.remove("power")    
    state.remove("watt")   
    state.remove("time")  
    state.remove("tzone")   
    state.remove("signal")    
    state.remove("powerOnState")
    state.remove("online")  
    state.remove("LastResponse")  
    state.remove("schedules") 
    state.remove("schedule1")
    state.remove("schedule2")
    state.remove("schedule3")
    state.remove("schedule4")
    state.remove("schedule5")
    state.remove("schedule6")
        
    interfaces.mqtt.disconnect()      // Guarantee we're disconnected  
    connect()                         // Reconnect to API Cloud  
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
