/***
 *  YoLink™ Plug (YS6604-UC), Plug w/Power Monitoring (YS6602-UC), and In-wall outlet (YS6704-UC)
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
 *  1.0.7: def temperatureScale()
 *  1.0.8: Fix donation URL
 *  1.1.0: Support Plug w/Power Monitoring (YS6602-UC)
 *  2.0.0: Reengineer driver to use centralized MQTT listener due to new YoLink service restrictions 
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.0"}

preferences {
    input title: "Driver Version", description: "YoLink™ Plug (YS6604-UC) or In-wall outlet (YS6704-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Please donate", description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Date Format Template Specifications", description: "<p>Click the link to view the possible letters used in timestamp formatting template. <a href=\"https://github.com/srbarcus/yolink/blob/main/DateFormats.txt\">Date Format Template Characters</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink Outlet Device", namespace: "srbarcus", author: "Steven Barcus") {     	
		capability "Polling"	
        capability "RelaySwitch"
        capability "Switch"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]]
        command "reset"        
        command "timestampFormat", [[name:"timestampFormat",type:"STRING", description:"Formatting template for event timestamp values. See Preferences below for details."]] 
        
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
        
        attribute "power", "String"     
        attribute "watt", "String"
        attribute "time1","String"
        attribute "watt1","String"
        attribute "time2","String"
        attribute "watt2","String"
        attribute "time3","String"
        attribute "watt3","String"
        attribute "time4","String"
        attribute "watt4","String"     
        
        attribute "alertInterval","String"
        attribute "powerLimitHigh","String"
        attribute "powerLimitLow","String"   
                
        attribute "overload","String"
        attribute "underload","String"
        attribute "remind","String"
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
   def power = object.data.power/10 
   def watt = object.data.watt 
   def firmware = object.data.version
   def time = object.data.time
   def tzone = object.data.tz
   def signal = object.data.loraInfo.signal     
    
   if (swState == "off") {power = 0} 
    
   logDebug("Parsed: DeviceId=$devId, Switch=$swState, Delay_on=$delay_on, Delay_off=$delay_off, Power=$power, Watt=$watt, Time=$time, Timezone=$tzone, Firmware=$firmware, Signal=$signal")      
                
   rememberState("online", "true")
   rememberState("switch", swState)
   rememberState("delay_on", delay_on)
   rememberState("delay_off", delay_off)
   rememberState("power", power)      
   rememberState("watt", watt) 
   rememberState("firmware", firmware)
   rememberState("time", time)
   rememberState("tzone", tzone)
   rememberState("signal", signal)                         
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
            def swState = parent.relayState(object.data.state)            
            def signal = object.data.loraInfo.signal                    
            def overload = object.data.alertType.overload
            def lowLoad = object.data.alertType.lowLoad
            def remind = object.data.alertType.remind
    
            logDebug("Parsed: swState=$swState, overload=$overload, lowLoad=$lowLoad, remind=$remind, signal=$signal")
            
            rememberState("switch",swState)
            rememberState("signal",signal)  
            rememberState("overload",overload)
            rememberState("underload",lowLoad)
            rememberState("remind",remind)
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
            
        case "powerReport":            
            def watts = object.data.watts
            logDebug("Watt Report = ${watts}")

            def time1 = watts.time[0]
            def watt1 = watts.watt[0]
            time1 = formatTimestamp(time1)  
                        
            def time2 = watts.time[1]
            def watt2 = watts.watt[1]
            time2 = formatTimestamp(time2)  
                        
            def time3 = watts.time[2]
            def watt3 = watts.watt[2]
            time3 = formatTimestamp(time3)  
                        
            def time4 = watts.time[3]
            def watt4 = watts.watt[3]            
            time4 = formatTimestamp(time4)  
                        
            logDebug("Parsed: time1 = ${time1}, watt1 = ${watt1}, time2 = ${time2}, watt2 = ${watt2}, time3 = ${time3}, watt3 = ${watt3}, time4 = ${time4}, watt4 = ${watt4}")
            
            rememberState("time1",time1)
            rememberState("watt1",watt1)
            rememberState("time2",time2)
            rememberState("watt2",watt2)
            rememberState("time3",time3)
            rememberState("watt3",watt3)
            rememberState("time4",time4)
            rememberState("watt4",watt4)
            
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

        case "setAlarm": 
            def alertInterval = object.data.alertInterval
            def powerLimitHigh = object.data.powerLimitHigh/10
            def powerLimitLow = object.data.powerLimitLow/10
    
            logDebug("Parsed: alertInterval=$alertInterval, powerLimitHigh=$powerLimitHigh, powerLimitLow=$powerLimitLow")
            
            rememberState("alertInterval",alertInterval)
            rememberState("powerLimitHigh",powerLimitHigh)
            rememberState("powerLimitLow",powerLimitLow)             
            break;    
            
        case "Alert":
            def swState = parent.relayState(object.data.state) 
            def overload = object.data.alertType.overload
            def lowLoad = object.data.alertType.lowLoad
            def remind = object.data.alertType.remind
            def power = object.data.power/10 
            def powerLimitHigh = object.data.powerLimitHigh/10
            def powerLimitLow = object.data.powerLimitLow/10
            def signal = object.data.loraInfo.signal 
            
            logDebug("Parsed: swState=$swState, overload=$overload, lowLoad=$lowLoad, remind=$remind, power=$power, powerLimitHigh=$powerLimitHigh, powerLimitLow=$powerLimitLow, signal=$signal")
            
            rememberState("swState",swState)
            rememberState("overload",overload)
            rememberState("underload",lowLoad)
            rememberState("remind",remind)
            rememberState("power",power)            
            rememberState("powerLimitHigh",powerLimitHigh)
            rememberState("powerLimitLow",powerLimitLow) 
            rememberState("signal",signal)            
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
    state.remove("firmware")
    state.remove("switch")
    state.remove("delay_ch")        //Note: remove in future
    state.remove("delay_on")
    state.remove("delay_off")       
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
    
    state.remove("power")    
    state.remove("watt")
    state.remove("time1")
    state.remove("watt1")
    state.remove("time2")
    state.remove("watt2")
    state.remove("time3")
    state.remove("watt3")
    state.remove("time4")
    state.remove("watt4")
    
    state.remove("alertInterval")
    state.remove("powerLimitHigh")
    state.remove("powerLimitLow")
    
    state.remove("overload")
    state.remove("underload")
    state.remove("remind")  
    
    state.timestampFormat = "MM/dd/yyyy hh:mm:ss a"     

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
