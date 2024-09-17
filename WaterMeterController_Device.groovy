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
 *  0.0.0: BETA release
 */

import groovy.json.JsonSlurper

def clientVersion() {return "0.0.0"}
def copyright() {return "<br>© 2022-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "Water Meter Controller v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"	
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Date Format Template Specifications"), description: "<p>Click the link to view the possible letters used in timestamp formatting template. <a href=\"https://github.com/srbarcus/yolink/blob/main/DateFormats.txt\">Date Format Template Characters</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink WaterMeterController Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"		
        capability "LiquidFlowRate"
		capability "Battery"
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
        attribute "reportAt", "String"        
                        
        attribute "timeZone", "String"
        attribute "alarmInterval", "String"  
        attribute "testingAlarm", "String"  
        attribute "alarmTestType", "String"
        attribute "alarmTestDay", "String"
        attribute "alarmTestTime", "String"   
        attribute "unexpected", "String"   
        attribute "lowBattery", "String"   
        attribute "smokeAlarm", "String"   
        attribute "gasAlarm", "String"   
        attribute "highTempAlarm", "String"   
        attribute "silence", "String"   
        attribute "gasAlarmChanged", "String" 
        attribute "smokeAlarmChanged", "String" 
        attribute "unexpectedChanged", "String" 
        attribute "lastTest", "String"
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
/*
data.state.valve	<String,Necessary>	Valve state, ["close","open"]
data.state.meter	<Integer,Necessary>	Meter reading
data.state.waterFlowing	<Boolean,Necessary>	Water flowing
data.alarm.openReminder	<Boolean,Necessary>	Open remind alarm
data.alarm.leak	<Boolean,Necessary>	Leak alarm
data.alarm.amountOverrun	<Boolean,Necessary>	Amount overrun alarm
data.alarm.durationOverrun	<Boolean,Necessary>	Duration overrun alarm
data.alarm.valveError	<Boolean,Necessary>	Valve error alarm
data.alarm.reminder	<Boolean,Necessary>	Remind repeat
data.alarm.freezeError	<Boolean,Necessary>	Freeze alarm
data.battery	<Integer,Necessary>	Level of device's battery, 0 to 4 means empty to full
data.powerSupply	<String,Necessary>	Power supply, ["battery","PowerLine"]
data.valveDelay.on	<Integer,Optional>	The remain time of Delay ON;Unit of minute;0 is OFF
data.valveDelay.off	<Integer,Optional>	The remain time of Delay OFF;Unit of minute;0 is OFF
data.attributes.openReminder	<Integer,Necessary>	Open remind duration in minute
data.attributes.meterUnit	<Integer,Necessary>	Meter screen unit, 0-GAL 1-CCF 2-M3 3-L
data.attributes.alertInterval	<Integer,Necessary>	Alert interval in minute
data.attributes.meterStepFactor	<Integer,Necessary>	Meter measurement accuracy
data.attributes.leakLimit	<Float,Necessary>	Leak limit in meter unit
data.attributes.autoCloseValve	<Boolean,Necessary>	Close valve if leak limit exceeded
data.attributes.overrunAmountACV	<Boolean,Necessary>	Overrun amount auto close valve
data.attributes.overrunDurationACV	<Boolean,Necessary>	Overrun duration auto close valve
data.attributes.leakPlan	<String,Necessary>	Leak plan mode, ["on","off","schedule"]
data.attributes.overrunAmount	<Float,Necessary>	Overrun amount in meter unit
data.attributes.overrunDuration	<Integer,Necessary>	Overrun duration in minute
data.attributes.freezeTemp	<Float,Necessary>	Freeze temperature in celsius
data.recentUsage.amount	<Integer,Necessary>	Recent usage in meter unit
data.recentUsage.duration	<Integer,Necessary>	Recent usage duration in minute
data.dailyUsage	<Integer,Necessary>	Daily usage in meter unit
data.temperature	<Float,Necessary>	Temperature in celsius
data.version	<String,Necessary>	Firmware version
data.tz	<Integer,Necessary>	Timezone of device. -12 ~ 12
*/
    
    def online = object.data.online     
    def reportAt = object.data.reportAt     
    def firmware = object.data.state.version.toUpperCase()   
    def battery = parent.batterylevel(object.data.state.battery) 
    
    def highAlarm = object.data.state.alarm.highAlarm
    def lowAlarm = object.data.state.alarm.lowAlarm
    def detectorError = object.data.state.alarm.detectorError
    
    def standby = object.data.state.alarmSettings.standby
    def interval = object.data.state.alarmSettings.interval
    def high = object.data.state.alarmSettings.high
    def low = object.data.state.alarmSettings.low
    
    def reportInterval = object.data.state.reportInterval
                   
    logDebug("Device State: online(${online}), " +
             "Report At(${reportAt}), " +
             "Firmware(${firmware}), " +
             "Battery(${battery}), " + 
             "highAlarm(${highAlarm}), " +
             "lowAlarm(${lowAlarm})," +
             "detectorError(${detectorError}), " +
             "standby(${standby}), " +   
             "interval(${interval}), " +
             "high(${high}), " +
             "Low(${low}), " + 
             "reportInterval(${reportInterval})")                  
        
   /* rememberState("online",online)
    rememberState("reportAt",reportAt)       
    rememberState("timeZone",timeZone)
    rememberState("firmware",firmware)
    rememberState("battery", battery, "%")
    rememberState("temperature", temperature, "°".plus(state.temperatureScale))    
    rememberState("alarmInterval",alarmInterval)
    rememberState("testingAlarm",testingAlarm)
    rememberState("alarmTestType",alarmTestType)
    rememberState("alarmTestDay",alarmTestDay)    
    rememberState("alarmTestTime",alarmTestTime)
    rememberState("unexpected",unexpected)
    rememberState("lowBattery",lowBattery)
    rememberState("smokeAlarm",smokeAlarm)             
    rememberState("gasAlarm",gasAlarm)
    rememberState("highTempAlarm",highTempAlarm)
    rememberState("silence",silence)            
    rememberState("gasAlarmChanged",gasAlarmChanged) 
    rememberState("smokeAlarmChanged",smokeAlarmChanged) 
    rememberState("unexpectedChanged",unexpectedChanged)              
    
    def carbonMonoxide
    def smoke
    
    if (gasAlarm == "true") {carbonMonoxide = "detected"} else {carbonMonoxide = "clear"}    
    if (smokeAlarm == "true") {smoke = "detected"} else {smoke = "clear" }    
    
    rememberState("carbonMonoxide",carbonMonoxide)              
    rememberState("smoke",smoke)  */                
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
		case "Report": case "StatusChange":
    //{"alarm":{"highAlarm":false,"lowAlarm":false,"detectorError":false,"reminder":false},"battery":1,"waterDepth":0,
    //"alarmSettings":{"standby":0,"interval":0,"high":999,"low":0},"reportInterval":60,"version":"0508","loraInfo":{"netId":"010201","signal":-27,"gatewayId":"d88b4c160300e5de","gateways":1}},"deviceId":"d88b4c0100082ab7"}]            
           def highAlarm = object.data.alarm.highAlarm
           def lowAlarm = object.data.alarm.lowAlarm 
           def detectorError = object.data.alarm.detectorError
           def reminder = object.data.alarm.reminder 
            
           def battery = parent.batterylevel(object.data.battery)   
            
           def waterDepth = object.data.waterDepth
            
           def standby = object.data.alarmSettings.standby
           def interval = object.data.alarmSettings.interval
           def high = object.data.alarmSettings.high
           def low = object.data.alarmSettings.low
           
           def reportInterval = object.data.reportInterval 
           def firmware = object.data.version.toUpperCase()   
                     
           def rssi = object.data.loraInfo.signal  
       
           logDebug("Firmware(${firmware}), " +
                 "Battery(${battery}), " + 
                 "RSSI(${rssi}), " +  
                 "highAlarm(${highAlarm}), " +
                 "lowAlarm(${lowAlarm}), " +
                 "detectorError(${detectorError}), " +
                 "reminder(${reminder}), " +   
                 "waterDepth:(${waterDepth}), " +
                 "standby(${standby}), " +   
                 "interval(${interval}), " +
                 "high(${high}), " +
                 "low(${low}), " +
                 "reportInterval(${reportInterval}")     

            /*
            rememberState("timeZone",timeZone)
            rememberState("firmware",firmware)
            rememberState("battery", battery, "%")
            rememberState("temperature", temperature, "°".plus(state.temperatureScale)) 
            fmtSignal(rssi)
            rememberState("alarmInterval",alarmInterval)
            rememberState("testingAlarm",testingAlarm)
            rememberState("lastTest",lastInspection)
            rememberState("alarmTestType",alarmTestType)
            rememberState("alarmTestDay",alarmTestDay)
            rememberState("alarmTestTime",alarmTestTime)
            rememberState("unexpected",unexpected)
            rememberState("lowBattery",lowBattery)
            rememberState("smokeAlarm",smokeAlarm)             
            rememberState("gasAlarm",gasAlarm)
            rememberState("highTempAlarm",highTempAlarm)
            rememberState("silence",silence)            
            rememberState("gasAlarmChanged",gasAlarmChanged) 
            rememberState("smokeAlarmChanged",smokeAlarmChanged) 
            rememberState("unexpectedChanged",unexpectedChanged)              
    
            def carbonMonoxide
            def smoke
           
            if (gasAlarm == "true") {carbonMonoxide = "detected"} else {carbonMonoxide = "clear"}    
            if (smokeAlarm == "true") {smoke = "detected"} else {smoke = "clear" }    
    
            rememberState("carbonMonoxide",carbonMonoxide)              
            rememberState("smoke",smoke)  
            */
 		    break;   
            
		case "setInterval":     
            def alertInterval = object.data.alertInterval 
            def rssi = object.data.loraInfo.signal 
            rememberState("alertInterval",alertInterval)            
            fmtSignal(rssi) 
            break; 
            
	    case "setSchedule":       
            def alarmTestType = object.data.type
            def alarmTestDay = object.data.day
            if (alarmTestType != "monthly") {alarmTestDay = parent.scheduledDay(alarmTestDay)} 
            def alarmTestTime = object.data.time
    
            logDebug("Alarm Test Type:(${alarmTestType}), " +
                     "Alarm Test Day(${alarmTestDay}), " +   
                     "Alarm Test Time(${alarmTestTime})")
            
            rememberState("alarmTestType",alarmTestType)
            rememberState("alarmTestDay",alarmTestDay)      //parent.scheduledDays(weekdays)
            rememberState("alarmTestTime",alarmTestTime)
            break;   
            
        case "setState":            
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
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("rssi")
    state.remove("signal")
    state.remove("reportAt")
    state.remove("timeZone")
    state.remove("firmware")
    state.remove("battery") 
    state.remove("temperature")
    state.remove("alarmInterval")
    state.remove("testingAlarm")
    state.remove("alarmTestType")
    state.remove("alarmTestDay")
    state.remove("alarmTestTime")
    state.remove("unexpected")
    state.remove("lowBattery")
    state.remove("smokeAlarm")
    state.remove("gasAlarm")
    state.remove("highTempAlarm")
    state.remove("silence")
    state.remove("gasAlarmChanged")
    state.remove("smokeAlarmChanged")
    state.remove("unexpectedChanged")
    state.remove("carbonMonoxide")
    state.remove("smoke")
      
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