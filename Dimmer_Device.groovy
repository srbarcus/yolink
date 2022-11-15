/***
 *  YoLink™ Dimmer (YS5707-UC)
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
 *  2.0.0: Initial Release
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.0"}

preferences {
    input title: "Driver Version", description: "YoLink™ Dimmer (YS5707-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Please donate", description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink Dimmer Device", namespace: "srbarcus", author: "Steven Barcus") {     	
		capability "Polling"	
        capability "Switch"
        capability "SignalStrength"  //rssi 
        capability "SwitchLevel"
                                      
        command "debug", [[name:"Debug Driver",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]]
        command "setLevel", [[name:"Brightness Level",type:"NUMBER", description:"Brightness level (0-100)"]]
        command "reset"   
        command "setTimer", [
                             [name:"Timer On",type:"NUMBER", description:"Minutes until timer turns on (0-1440)"],
                             [name:"Timer Off",type:"NUMBER", description:"Minutes until timer turns off (0-1440)"]
                            ]
        command "cancelTimer"
        
        attribute "online", "String"
        attribute "firmware", "String"  
        attribute "rssi", "String"      
        attribute "lastResponse", "String" 
        
        attribute "timerOn", "Number"  
        attribute "timerOff", "Number"  
        attribute "timerBrightess", "Number"  
        attribute "powerOnState", "String"  
        attribute "schedules", "integer"
        attribute "schedule1", "String"
        attribute "schedule2", "String"
        attribute "schedule3", "String"
        attribute "schedule4", "String"
        attribute "schedule5", "String"
        attribute "schedule6", "String"   
        
        attribute "gentle_on", "Number"  
        attribute "gentle_off", "Number"  
        attribute "statusLED", "String"
        attribute "levelLED", "String"
        attribute "calibration", "Number"  
   
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
    
    runIn(1,getSchedules)
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

def setLevel (level) {
    if (level > 100) {level=100}
    
    def swState = state.switch
    if (swState == "off") {
        swState = "close"
    } else {
        swState = "open"
    }    
    setSwitch(swState, level)
    
    runIn(1, getDevicestate)
}   

def cancelTimer() {
    setTimer(0, 0)
}    

def setTimer(timeon, timeoff) {
   if (timeon  > 1440) {timeon  = 1440} 
   if (timeoff > 1440) {timeoff = 1440} 
    
   logDebug("setTimer: On=${timeon}, Off=${timeoff}")  
   
   def params = [:] 
   params.put("delayOn", timeon)
   params.put("delayOff", timeoff)   
    
   def request = [:] 
   request.put("method", "${state.type}.setDelay")                  
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
          
        if (object) {
            logDebug("setTimer(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {               
             
               def timerOn = object.data.delayOn
               def timerOff = object.data.delayOff
               def timerBrightness = object.data.brightness 
   
               rememberState("timerOn", timerOn)
               rememberState("timerOff", timerOff)
               rememberState("timerBrightness", timerBrightness)  
                               
            } else {                
                if (notConnected(object)) { //Cannot connect to Device - YoLink bug? Device appears offline while state is changing
                   getDevicestate()  
                } else {
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
                }
            }                     
                                        
            return 							
                
	    } else { 			               
            log.error "setTimer() failed"
            lastResponse("setTimer() failed")     
        }     		
	} catch (e) {	
        log.error "setTimer() exception: $e"
        lastResponse("Error ${e}")     
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

//:Success, data:[state:open, brightness:38, deviceAttributes:[gradient:[on:2, off:2], led:[status:on, level:on], calibration:0], delay:[on:0, off:0, brightness:0], version:0701, moduleVersion:2, time:2022-11-12T13:44:01.000Z, tz:0, loraInfo:[signal:-29
def parseDevice(object) { 
   def swState = parent.relayState(object.data.state)
   def level = object.data.brightness
   def gentle_on = object.data.deviceAttributes.gradient.on
   def gentle_off = object.data.deviceAttributes.gradient.off
    
   def statusLED = object.data.deviceAttributes.led.status
   def levelLED = object.data.deviceAttributes.led.level
    
   def calibration = object.data.deviceAttributes.calibration 
    
   def timerOn = object.data.delay.on
   def timerOff = object.data.delay.off   
   def timerBrightness = object.data.delay.brightness 
   
   def firmware = object.data.version.toUpperCase()
   
   def rssi = object.data.loraInfo.signal         
     
   logDebug("Parsed: Switch=$swState, level=$level, gentle_on=$gentle_on, gentle_off=$gentle_off, statusLED=$statusLED, levelLED=$levelLED, calibration=$calibration, timerOn=$timerOn, timerOff=$timerOff, timerBrightness=$timerBrightness, Firmware=$firmware, Signal=$signal")      
                
   rememberState("online", "true")
   rememberState("switch", swState)
   rememberState("level", level) 
   rememberState("gentle_on", gentle_on)
   rememberState("gentle_off", gentle_off)
   rememberState("statusLED", statusLED)
   rememberState("levelLED", levelLED)
   rememberState("calibration", calibration) 
   rememberState("timerOn", timerOn)
   rememberState("timerOff", timerOff)
   rememberState("timerBrightness", timerBrightness)      
   rememberState("firmware", firmware) 
   rememberState("rssi", rssi)                         
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
        case "setDelay":     
            def timerOn = object.data.delayOn
            def timerOff = object.data.delayOff
            def timerBrightness = object.data.brightness 
   
            rememberState("timerOn", timerOn)
            rememberState("timerOff", timerOff)
            rememberState("timerBrightness", timerBrightness)
			break;    
            
        case "setDeviceAttributes":            
            def gentle_on = object.data.deviceAttributes.gradient.on
            def gentle_off = object.data.deviceAttributes.gradient.off
            def statusLED = object.data.deviceAttributes.led.status
            def levelLED = object.data.deviceAttributes.led.level
            def calibration = object.data.deviceAttributes.calibration 
     
            logDebug("Parsed: gentle_on=$gentle_on, gentle_off=$gentle_off, statusLED=$statusLED, levelLED=$levelLED, calibration=$calibration")      
                
            rememberState("online", "true")
            rememberState("gentle_on", gentle_on)
            rememberState("gentle_off", gentle_off)
            rememberState("statusLED", statusLED)
            rememberState("levelLED", levelLED)
            rememberState("calibration", calibration) 
   		break;  
            
		case "setInitState":
            def powerOnState = parent.relayState(object.data.initState) 
            
            rememberState("powerOnState",powerOnState)                 
			break;
            
		case "setTimeZone":
  			break;
        
        case "StatusChange":    
        case "setState":
            def swState = parent.relayState(object.data.state)   
            def rssi = object.data.loraInfo.signal   
            def level = object.data.brightness
    
            logDebug("Parsed: Switch=$swState, level=$level, rssi=$rssi")
            
            if (swState == "off") {
              rememberState("timerOn", 0)
              rememberState("timerOff", 0)
            }   
            
            rememberState("switch",swState)
            rememberState("level", level) 
            rememberState("rssi",rssi)                                       
			break;  
            
        case "getState":           
		case "Report":
            parseDevice(object)
			break;	

        case "setSchedules":
            parseSchedules(object)             
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

def setSwitch(setState, brightness=null) {
   def params = [:] 
   params.put("state", setState)    
    
   if (brightness != null) {
     params.put("brightness", brightness)      
   }     
    
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
                def rssi = object.data.loraInfo.signal       
                logDebug("Parsed: Switch=$swState, rssi=$rssi")
                rememberState("switch",swState)
                rememberState("rssi",rssi)  
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
            log.error "setSwitch() failed"
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

def getSchedules() {
   def request = [:] 
   request.put("method", "${state.type}.getSchedules")                  
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
          
        if (object) {
            logDebug("getSchedules(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {               
                parseSchedules(object)   
                               
            } else {                
                if (notConnected(object)) { //Cannot connect to Device - YoLink bug? Device appears offline while state is changing
                   getDevicestate()  
                } else {
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
                }
            }                     
                                        
            return 							
                
	    } else { 			               
            log.error "getSchedules() failed"
            lastResponse("getSchedules() failed")     
        }     		
	} catch (e) {	
        log.error "getSchedules() exception: $e"
        lastResponse("Error ${e}")     
	} 
}  

def parseSchedules(object) {
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
                  def schedule  =  object.data."${schedNum}".toString() //{isValid=true, week=127, index=1, on=21:3, off=21:4, brightness=90
                  
                  def weekhex =  object.data."${schedNum}".week 
                  def weekdays = parent.scheduledDays(weekhex)
                  logDebug("Days of week: ${weekdays}")
                                 
                  logDebug("schedule ${schedule}")
                                                   
                  schedule = schedule.replaceAll(" week=${weekhex},"," days=[${weekdays}],")                                          
                  schedule = schedule.replaceAll(" index=${schedNum},","")                       
                  schedule = schedule.replaceAll("isValid=","enabled=")
                  schedule = schedule.replaceAll("brightness","level")  
                  schedule = schedule.replaceAll("=25:0","=never")    
                    
                  logDebug("schedule ${schedule}")  
                    
                  def onHm =  object.data."${schedNum}".on 
                  on = Date.parse('H:m', onHm).format('HH:mm')
                  ontext = Date.parse('H:m', on).format('h:mm a')
                  schedule = schedule.replaceAll("on=${onHm}","on=${on} (${ontext})")   
                  
                  def offHm =  object.data."${schedNum}".off     
                  def off = Date.parse('H:m', offHm).format('HH:mm')  
                  def offtext = Date.parse('H:m', off).format('h:mm a')
                  schedule = schedule.replaceAll("off=${offHm}","off=${off} (${offtext})")     
                  
                  scheds++  
                  logDebug("Schedule ${scheds}: ${schedule}")
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
}    

def reset(){          
    state.remove("firmware")
    state.remove("switch")    
    state.remove("timerOn")
    state.remove("timerOff")       
    state.remove("timerBrightness")
    state.remove("rssi")    
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
    state.remove("level")  

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
