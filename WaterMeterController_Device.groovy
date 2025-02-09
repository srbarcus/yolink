/***
 *  YoLink™ Water Meter Controller 
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
 *  0.0.0: ALPHA release
 *  0.0.1: ALPHA release - Added "Report" message processing
 *  0.1.0: BETA release - Cleaned up code
 */

import groovy.json.JsonSlurper

def clientVersion() {return "0.1.0"}
def copyright() {return "<br>© 2025-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "Water Meter Controller v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"	
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Date Format Template Specifications"), description: "<p>Click the link to view the possible letters used in timestamp formatting template. <a href=\"https://github.com/srbarcus/yolink/blob/main/DateFormats.txt\">Date Format Template Characters</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink WaterMeterController Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
	capability "Polling"		
        capability "LiquidFlowRate"             //rate - NUMBER, unit:LPM || GPM
	capability "Battery"
        capability "PowerSource"                //ENUM ["battery", "dc", "mains", "unknown"]
        capability "TemperatureMeasurement"    
        capability "Alarm"                      //alarm - ENUM ["strobe", "off", "both", "siren"] both() off() siren() strobe()
        capability "SignalStrength"             //rssi 
        capability "Valve" //valve - ENUM ["open", "closed"] Commands close() open()
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]] 
        command "reset"  
        command "timestampFormat", [[name:"timestampFormat",type:"STRING", description:"Formatting template for event timestamp values. See Preferences below for details."]] 
       
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"          
        attribute "signal", "String"
        attribute "lastPoll", "String"
        attribute "lastResponse", "String"    

        attribute "meter", "String"
        attribute "waterFlowing", "String"
        attribute "openReminderAlarm", "String"
        attribute "leak", "String"
        attribute "amountOverrun", "String"
        attribute "durationOverrun", "String"
        attribute "valveError", "String"
        attribute "reminder", "String"
        attribute "freezeError", "String"
        attribute "battery", "String"
        attribute "powerSupply", "String"
        attribute "valveDelayOn", "String"
        attribute "valveDelayOff", "String"
        attribute "openReminder", "String"
        attribute "meterUnit", "String"
        attribute "alertInterval", "String"
        attribute "meterStepFactor", "String"
        attribute "leakLimit", "String"
        attribute "autoCloseValve", "String"
        attribute "overrunAmountACV", "String"
        attribute "overrunDurationACV", "String"
        attribute "leakPlan", "String"
        attribute "overrunAmount", "String"
        attribute "overrunDuration", "String"
        attribute "freezeTemp", "String"
        attribute "amount", "String"
        attribute "duration", "String"
        attribute "dailyUsage", "String"
        attribute "temperature", "String"
        attribute "timeZone", "String"
        attribute "screenDuration", "String"
        attribute "screenMeterUnit", "String"
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

def open () {
   setValve("open")   
}
    
def close () {
   setValve("close")  
}   

def temperatureScale(value) {
    state.temperatureScale = value
 }

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
   if (value == "true") {
     log.info "Debugging enabled"
   } else {
     log.info "Debugging disabled"
   }    
}

def setValve(setState) {
   def params = [:] 
   params.put("valve", setState)    
    
   def request = [:] 
   request.put("method", "${state.type}.setState")      
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
      def object = parent.pollAPI(request, state.name, state.type)  
       
      logDebug("setValve Result: ${object}")   
       
      if (successful(object)) {                
        parseDevice(object)                     
        lastResponse("Success") 
      } else {  //Error
         pollError(object)                
      }      
       
      //def valve = object.data.state
      //def rssi = object.data.loraInfo.signal 
       
      //rememberState("valve", valve, null, (device.currentValue("valve") =="unknown"))  
  
      //fmtSignal(rssi)   
   
    } catch (e) {	
        log.error "setValve() exception: $e"
        lastResponse("Error ${e}")     
        //sendEvent(name:"valve", value: "unknown", isStateChange:true)
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
    logDebug("parseDevice(${object})")
  
    def valve = object.data.state.valve
    def meter = object.data.state.meter                                  //Meter reading, Integer
    def waterFlowing = object.data.state.waterFlowing                    //Water flowing, Boolean
    def openReminderAlarm = object.data.alarm.openReminder               //Open remind alarm, Boolean
    def leak = object.data.alarm.leak                                    //Leak alarm, Boolean
    def amountOverrun = object.data.alarm.amountOverrun                  //Amount overrun alarm, Boolean
    def durationOverrun = object.data.alarm.durationOverrun              //Duration overrun alarm, Boolean
    def valveError = object.data.alarm.valveError                        //Valve error alarm, Boolean
    def reminder = object.data.alarm.reminder                            //Remind repeat, Boolean
    def freezeError = object.data.alarm.freezeError                      //Freeze alarm, Boolean
    def battery = parent.batterylevel(object.data.state.battery)         //Level of device's battery, 0 to 4 means empty to full 
    def powerSupply = object.data.powerSupply                            //Power supply, ["battery","PowerLine"]
    def valveDelayOn = object.data.valveDelay.on                         //The remain time of Delay ON;Unit of minute;0 is OFF, Integer
    def valveDelayOff = object.data.valveDelay.off                       //The remain time of Delay OFF;Unit of minute;0 is OFF, Integer
    def openReminder = object.data.attributes.openReminder               //Open remind duration in minute, Integer
    def meterUnit = object.data.attributes.meterUnit                     //Meter screen unit, 0-GAL 1-CCF 2-M3 3-L, Integer
    def alertInterval = object.data.attributes.alertInterval             //Alert interval in minute, Integer
    def meterStepFactor = object.data.attributes.meterStepFactor         //Meter measurement accuracy, Integer
    def leakLimit = object.data.attributes.leakLimit                     //Leak limit in meter unit, Float
    def autoCloseValve = object.data.attributes.autoCloseValve           //Close valve if leak limit exceeded, Boolean
    def overrunAmountACV = object.data.attributes.overrunAmountACV       //Overrun amount auto close valve, Boolean
    def overrunDurationACV = object.data.attributes.overrunDurationACV   //Overrun duration auto close valve, Boolean
    def leakPlan = object.data.attributes.leakPlan                       //Leak plan mode, ["on","off","schedule"]
    def overrunAmount = object.data.attributes.overrunAmount             //Overrun amount in meter unit, Float
    def overrunDuration = object.data.attributes.overrunDuration         //Overrun duration in minute, Integer
    def freezeTemp = object.data.attributes.freezeTemp                   //Freeze temperature in celsius, Float
    def amount = object.data.recentUsage.amount                          //Recent usage in meter unit, Integer
    def duration = object.data.recentUsage.duration                      //Recent usage duration in minute, Integer
    def dailyUsage = object.data.dailyUsage                              //Daily usage in meter unit, Integer
    def temperature = object.data.temperature                            //Temperature in celsius, Float
    def firmware = object.data.version.toUpperCase()                     //Firmware version  
    def timeZone = object.data.tz                                        //Timezone of device. -12 ~ 12
         
    
    rememberState("valve", valve)
    rememberState("meter", meter)
    rememberState("waterFlowing", waterFlowing)
    rememberState("openReminderAlarm", openReminderAlarm)
    rememberState("leak", leak)
    rememberState("amountOverrun", amountOverrun)
    rememberState("durationOverrun", durationOverrun)
    rememberState("valveError", valveError)
    rememberState("reminder", reminder)
    rememberState("freezeError", freezeError)
    rememberState("battery", battery)
    rememberState("powerSupply", powerSupply)
    rememberState("valveDelayOn", valveDelayOn)
    rememberState("valveDelayOff", valveDelayOff)
    rememberState("openReminder", openReminder)
    rememberState("meterUnit", meterUnit)
    rememberState("alertInterval", alertInterval)
    rememberState("meterStepFactor", meterStepFactor)
    rememberState("leakLimit", leakLimit)
    rememberState("autoCloseValve", autoCloseValve)
    rememberState("overrunAmountACV", overrunAmountACV)
    rememberState("overrunDurationACV", overrunDurationACV)
    rememberState("leakPlan", leakPlan)
    rememberState("overrunAmount", overrunAmount)
    rememberState("overrunDuration", overrunDuration)
    rememberState("freezeTemp", freezeTemp)
    rememberState("amount", amount)
    rememberState("duration", duration)
    rememberState("dailyUsage", dailyUsage)
    rememberState("temperature", temperature)
    rememberState("firmware", firmware)
    rememberState("timeZone", timeZone)
                   
    logDebug("parseDevice() Parsed: " +
	"valve(${valve}), " +
	"meter(${meter}), " +
	"waterFlowing(${waterFlowing}), " +
	"openReminder(${openReminder}), " +
	"leak(${leak}), " +
	"amountOverrun(${amountOverrun}), " +
	"durationOverrun(${durationOverrun}), " +
	"valveError(${valveError}), " +
	"reminder(${reminder}), " +
	"freezeError(${freezeError}), " +
	"battery(${battery}), " +
	"powerSupply(${powerSupply}), " +
	"valveDelayOn(${valveDelayOn}), " +
	"valveDelayOff(${valveDelayOff}), " +
	"openReminder(${openReminder}), " +
	"meterUnit(${meterUnit}), " +
	"alertInterval(${alertInterval}), " +
	"meterStepFactor(${meterStepFactor}), " +
	"leakLimit(${leakLimit}), " +
	"autoCloseValve(${autoCloseValve}), " +
	"overrunAmountACV(${overrunAmountACV}), " +
	"overrunDurationACV(${overrunDurationACV}), " +
	"leakPlan(${leakPlan}), " +
	"overrunAmount(${overrunAmount}), " +
	"overrunDuration(${overrunDuration}), " +
	"freezeTemp(${freezeTemp}), " +
	"amount(${amount}), " +
	"duration(${duration}), " +
	"dailyUsage(${dailyUsage}), " +
	"temperature(${temperature}), " +
	"firmware(${firmware}), " +
	"timeZone(${timeZone})"
        )                  
}   
 
def parse(topic) {     
     processStateData(topic.payload)
}

def void processStateData(payload) {
    rememberState("online","true") 
    
    def object = new JsonSlurper().parseText(payload)    
    def devId = object.deviceId      
    
    if (state.devId == devId) {  // Only handle if message is for me         
        logDebug("processStateData(${object})")
        
        def child = parent.getChildDevice(state.my_dni)
        def name = child.getLabel()                
        def event = object.event.replace("${state.type}.","")
        logDebug("Received Message Type: ${event} for: $name")        
        
        switch(event) {
        case "Report": 
            def valve = object.data.state.valve                                  //Valve state, ["close","open"]
            def meter = object.data.state.meter                                  //Meter reading, Integer
            def openReminderAlarm = object.data.alarm.openReminder               //Open remind alarm, Boolean
            def leak = object.data.alarm.leak                                    //Leak alarm, Boolean
            def amountOverrun = object.data.alarm.amountOverrun                  //Amount overrun alarm, Boolean
            def durationOverrun = object.data.alarm.durationOverrun              //Duration overrun alarm, Boolean
            def valveError = object.data.alarm.valveError                        //Valve error alarm, Boolean
            def reminder = object.data.alarm.reminder                            //Remind repeat, Boolean
            def freezeError = object.data.alarm.freezeError                      //Freeze alarm, Boolean
            def battery = parent.batterylevel(object.data.state.battery)         //Level of device's battery, 0 to 4 means empty to full 
            def powerSupply = object.data.powerSupply                            //Power supply, ["battery","PowerLine"]
            def alertInterval = object.data.attributes.alertInterval             //Alert interval in minute, Integer
            def screenDuration = object.data.attributes.screenDuration  
            def screenMeterUnit = object.data.attributes.screenMeterUnit 
            def meterUnit = object.data.attributes.meterUnit       
            def meterStepFactor = object.data.attributes.meterStepFactor         //Meter measurement accuracy, Integer
            def leakLimit = object.data.attributes.leakLimit                     //Leak limit in meter unit, Float
            def leakPlan = object.data.attributes.leakPlan                       //Leak plan mode, ["on","off","schedule"]
            def overrunAmount = object.data.attributes.overrunAmount             //Overrun amount in meter unit, Float
            def overrunDuration = object.data.attributes.overrunDuration         //Overrun duration in minute, Integer
            def amount = object.data.recentUsage.amount                          //Recent usage in meter unit, Integer
            def duration = object.data.recentUsage.duration                      //Recent usage duration in minute, Integer
            def firmware = object.data.version.toUpperCase()                     //Firmware version  
            
            def rssi = object.data.loraInfo.signal
            fmtSignal(rssi)  

            rememberState("valve", valve)
            rememberState("meter", meter)
            rememberState("openReminderAlarm", openReminderAlarm)
            rememberState("leak", leak)
            rememberState("amountOverrun", amountOverrun)
            rememberState("durationOverrun", durationOverrun)
            rememberState("valveError", valveError)
            rememberState("reminder", reminder)
            rememberState("freezeError", freezeError)
            rememberState("battery", battery)
            rememberState("powerSupply", powerSupply)
            rememberState("valveDelayOn", valveDelayOn)
            rememberState("valveDelayOff", valveDelayOff)
            rememberState("openReminder", openReminder)            
            rememberState("screenDuration", screenDuration)
            rememberState("screenMeterUnit", screenMeterUnit)            
            rememberState("meterUnit", meterUnit)
            rememberState("alertInterval", alertInterval)
            rememberState("meterStepFactor", meterStepFactor)
            rememberState("leakLimit", leakLimit)
            rememberState("autoCloseValve", autoCloseValve)
            rememberState("overrunAmountACV", overrunAmountACV)
            rememberState("overrunDurationACV", overrunDurationACV)
            rememberState("leakPlan", leakPlan)
            rememberState("overrunAmount", overrunAmount)
            rememberState("overrunDuration", overrunDuration)
            rememberState("amount", amount)
            rememberState("duration", duration)            
            rememberState("firmware", firmware)
            rememberState("timeZone", timeZone)
                   
            logDebug("processStateData() Parsed: " +
            "valve(${valve}), " +
            "meter(${meter}), " + 
            "openReminder(${openReminder}), " +
            "leak(${leak}), " +
            "amountOverrun(${amountOverrun}), " +
            "durationOverrun(${durationOverrun}), " +
            "valveError(${valveError}), " +
            "reminder(${reminder}), " +
            "freezeError(${freezeError}), " +
            "battery(${battery}), " +
            "powerSupply(${powerSupply}), " +
            "openReminder(${openReminder}), " +
            "screenDuration(${screenDuration}), " +
            "screenMeterUnit(${screenMeterUnit}), " +
            "meterUnit(${meterUnit}), " +
            "alertInterval(${alertInterval}), " +
            "meterStepFactor(${meterStepFactor}), " +
            "leakLimit(${leakLimit}), " +
            "autoCloseValve(${autoCloseValve}), " +
            "overrunAmountACV(${overrunAmountACV}), " +
            "overrunDurationACV(${overrunDurationACV}), " +
            "leakPlan(${leakPlan}), " +
            "overrunAmount(${overrunAmount}), " +
            "overrunDuration(${overrunDuration}), " +
            "amount(${amount}), " +
            "duration(${duration}), " +
            "firmware(${firmware}), " +
            "rssi(${rssi}), " +                     
            "timeZone(${timeZone})"
            )                  
                            
            break;      
            
           
	default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	}				
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
    rememberState("debug", "false") 
    
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("rssi")
    state.remove("signal")
    state.remove("timeZone")
    state.remove("firmware")
    state.remove("battery") 
    state.remove("temperature")
    
    state.remove("valve")
    state.remove("meter")
    state.remove("waterFlowing")
    state.remove("openReminder")
    state.remove("leak")
    state.remove("amountOverrun")
    state.remove("durationOverrun")
    state.remove("valveError")
    state.remove("reminder")
    state.remove("freezeError")
    state.remove("battery")
    state.remove("powerSupply")
    state.remove("valveDelayOn")
    state.remove("valveDelayOff")
    state.remove("openReminder")
    state.remove("meterUnit")
    state.remove("alertInterval")
    state.remove("meterStepFactor")
    state.remove("leakLimit")
    state.remove("autoCloseValve")
    state.remove("overrunAmountACV")
    state.remove("overrunDurationACV")
    state.remove("leakPlan")
    state.remove("overrunAmount")
    state.remove("overrunDuration")
    state.remove("freezeTemp")
    state.remove("amount")
    state.remove("duration")
    state.remove("dailyUsage")
    state.remove("temperature")
    state.remove("tz")
    state.remove("screenDuration")
    state.remove("screenMeterUnit")
      
    state.timestampFormat = "MM/dd/yyyy hh:mm:ss a" 

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