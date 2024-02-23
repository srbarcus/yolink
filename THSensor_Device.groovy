/***
 *  YoLink™ Temperature Humidity Sensor (YS8003-UC) and YoLink™ X3 SMART TEMPERATURE & HUMIDITY SENSOR (YS8006-UC)
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
 *  1.0.1: Removed undocumented parameters, "Period" and "Code"
 *         - Corrected event parsing causing "null" signal 
 *         - Corrected errors in poll() 
 *  1.0.2: Corrected ServiceSetup error
 *  1.0.3: Fixed clientVersion()
 *  1.0.4: Fixed parsing error: groovy.lang.MissingPropertyException: No such property: state for class: java.lang.String on line nnn (method parse)
 *  1.0.5: Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 *         - Removed superfluous code, correct attribute types, correct attributes to match standards, correct data to match attribute, remove math import
 *  1.0.6: Fix syncing of Temperature scale with YoLink™ Device Service app
 *  1.0.7: Fix donation URL
 *  1.0.8: Added getSetup()
 *  2.0.0: Reengineer driver to use centralized MQTT listener due to new YoLink service restrictions 
 *  2.0.1: Multiple Fixes, add 'Alarm' capability (status only)
 *  2.0.2: Added 'Alarm' capability (status only)
 *  2.0.3: Added 'temperatureScale' command
 *         - Corrected incorrect values if calibrations were specified
 *  2.0.4: Support diagnostics, correct various errors, make singleThreaded
 *  2.0.5: Support X3 SMART TEMPERATURE & HUMIDITY SENSOR (YS8006-UC)
 *         - Handle X3 "THSensor.DataRecord" events
 *         - Add "%rh" unit to Humidity attribute
 *         - Add formatted "signal" attribute as rssi & " dBm"
 *         - Add capability "SignalStrength"  
 *         - Add unit values to: temperature, battery
 *  2.0.6: Fix handling of X3 "THSensor.DataRecord" events
 *         - Handle X3 "THSensor.getState" events
 *  2.0.7: Prevent Service app from waiting on device polling completion
 *         - Add temperature history tracking attributes: "highDay", "lowDay", "highest", "lowest", "highestDate", "lowestDate"
 *  2.0.8: Updated driver version on poll
 *  2.0.9: Support "setDeviceToken()"
 *         - Update copyright
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.9"}
def copyright() {return "<br>© 2022-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "YoLink™ Temperature Humidity Sensor (YS8003-UC) and X3 T&H Sensor (YS8006-UC) v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink THSensor Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"				
		capability "Battery"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "Alarm" //ENUM ["strobe", "off", "both", "siren"]
        capability "SignalStrength"             //rssi
        
        command "debug", [[name:"Debug Driver",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]]  
        command "temperatureScale", [[name:"Temperature Scale",type:"ENUM", description:"Temperature reporting scale (Fahrenheit or Celsius)", constraints:["F", "C"]]]
        command "resetDayHighLow"
        command "resetHighLow"
        command "reset"                  
              
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"  
        attribute "signal", "String"
        attribute "lastPoll", "String"
        attribute "lastResponse", "String" 
        
        attribute "lowBattery", "String"  
        attribute "lowTemp", "String"  
        attribute "highTemp", "String"
        attribute "temperatureScale", "String"
        attribute "state", "String"
        attribute "tempCorrection", "String"
        attribute "tempLimitMax", "String"
        attribute "tempLimitMin", "String"
        attribute "alertInterval", "String"
        attribute "currentDay", "String"
        attribute "highDay", "Number"
        attribute "lowDay", "Number"
        attribute "highest", "Number"        
        attribute "lowest", "Number"
        attribute "highestDate", "String"        
        attribute "lowestDate", "String"
        
        attribute "lowHumidity", "String"
        attribute "highHumidity", "String" 
        attribute "humidityCorrection", "String" 
        attribute "humidityLimitMax", "String"   
        attribute "humidityLimitMin", "String"
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
    rememberState("temperatureScale", value)
 }

def debug(value) { 
   rememberState("debug",value)
   if (value == "true") {
     log.info "Debugging enabled"
   } else {
     log.info "Debugging disabled"
   }    
}

def resetDayHighLow() {
    state.remove("currentDay")    
    state.remove("highDay")    
    state.remove("lowDay")    
}    

def resetHighLow() {
    state.remove("highest")
    state.remove("lowest")    
}    

def setHighLow(temp) {
   logDebug("setHighLow(${temp}): Date: ${state?.currentDay} History: ${state?.highest}/${state?.lowest} Day: ${state?.highDay}/${state?.lowDay}")  
    
   def todate = new Date(now())    
   def today = todate.format("MM/dd/yyyy")
   def todayDetail = todate.format("MM/dd/yyyy hh:mm:ss a")
    
   if ((temp >= state?.highest) || (state?.highest==null)) {
      rememberState("highest",temp)
      rememberState("highestDate",todayDetail)
   }
    
   if ((temp <= state?.lowest) || (state?.lowest==null)) {
      rememberState("lowest",temp)
      rememberState("lowestDate",todayDetail)
   }
    
   if (state?.currentDay !=  today) { 
      state.remove("highDay")    
      state.remove("lowDay")
      state.currentDay = today   
   }
        
   if ((temp > state?.highDay) || (state?.highDay==null)) {
       rememberState("highDay",temp)
   }
    
   if ((temp < state?.lowDay) || (state?.lowDay)==null) {
       rememberState("lowDay",temp)
   } 
    
   logDebug("setHighLow(${temp}) exit: Date: ${state?.currentDay} History: ${state?.highest}/${state?.lowest} Day: ${state?.highDay}/${state?.lowDay}")   
}  

def both() {log.error "'both' command is not supported"}
def off() {log.error "'off' command is not supported"}
def siren() {log.error "'siren' command is not supported"}
def strobe() {log.error "'strobe' command is not supported"}

def getDevicestate() {
    state.driver=clientVersion()
    
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
                parseDevice(object,"devicestate")                     
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

def parseDevice(object, source) {
    logDebug("parseDevice() Source: ${source}")
    
    def online
    def lowBattery
    def lowTemp
    def highTemp
    def battery
    def batteryType
    def alertInterval
    def devstate
    def tempCorrection
    def tempLimitMax 
    def tempLimitMin
    def temperature
    def firmware
    def rssi
    def humidity
    def lowHumidity
    def highHumidity
    def humidityCorrection
    def humidityLimitMax
    def humidityLimitMin
    def recordInterval
    
    switch(source) {
		case "devicestate":
            online           = object.data.online.toString()
            devstate         = object.data.state.state
            lowBattery       = object.data.state.alarm.lowBattery.toString()
            lowTemp          = object.data.state.alarm.lowTemp.toString()
            highTemp         = object.data.state.alarm.highTemp.toString()
            battery          = parent.batterylevel(object.data.state.battery)
            alertInterval    = object.data.state.interval
            tempCorrection   = object.data.state.tempCorrection
            tempLimitMax     = object.data.state.tempLimit.max
            tempLimitMin     = object.data.state.tempLimit.min 
            temperature      = object.data.state.temperature  
            firmware         = object.data.state.version.toUpperCase()   

            humidity           = object.data.state.humidity 
            humidityCorrection = object.data.state.humidityCorrection
            humidityLimitMax   = object.data.state.humidityLimit.max
            humidityLimitMin   = object.data.state.humidityLimit.min
            lowHumidity        = object.data.state.alarm.lowHumidity.toString()
            highHumidity       = object.data.state.alarm.highHumidity.toString()
            
            logDebug("Device State Reported: Temperature(${temperature}), Temp Limit Max(${tempLimitMax}), Temp Limit Min(${tempLimitMin})")
            
            temperature =  parent.convertTemperature(temperature,state.temperatureScale)
            tempLimitMax = parent.convertTemperature(tempLimitMax,state.temperatureScale)
            tempLimitMin = parent.convertTemperature(tempLimitMin,state.temperatureScale)

            logDebug("Device State Converted: Temperature(${temperature}), Temp Limit Max(${tempLimitMax}), Temp Limit Min(${tempLimitMin})")

            rememberState("online",online)    
           
            rememberState("lowBattery",lowBattery)  
        
            alarmState(temperature,tempLimitMin,tempLimitMax,humidity,humidityLimitMin,humidityLimitMax)
        
            setHighLow(temperature)

            rememberState("battery", battery, "%")    
            rememberState("alertInterval",alertInterval)
            rememberState("tempCorrection",tempCorrection)
            rememberState("tempLimitMax",tempLimitMax)
            rememberState("tempLimitMin",tempLimitMin)
            rememberState("temperature", temperature, "°".plus(state.temperatureScale))
            rememberState("firmware",firmware)

            rememberState("humidity",humidity,"%rh")
            rememberState("humidityCorrection",humidityCorrection)
            rememberState("humidityLimitMax",humidityLimitMax)
            rememberState("humidityLimitMin",humidityLimitMin)
             
            logDebug("Device State: online(${online}), " +
                     "State(${devstate}), " + 
                     "Firmware(${firmware}), " +
                     "Low Battery(${lowBattery}), " +
                     "Low Temp:(${lowTemp}), " +
                     "Hight Temp(${highTemp}), " +
                     "Battery(${battery}), " +
                     "Alert Interval(${alertInterval}), " +
                     "Temp Correction(${tempCorrection}), " + 
                     "Temp Limit Max(${tempLimitMax}), " + 
                     "Temp Limit Min(${tempLimitMin}), " +   
                     "Temperature(${temperature}), " +
                     "Low Humidity(${lowHumidity}), " +
                     "High Humidity(${highHumidity}), " +                     
                     "Humidity(${humidity}), " + 
                     "Humidity Correction(${humidityCorrection}), " + 
                     "Humidity Limit Max(${humidityLimitMax}), " + 
                     "Humidity Limit Min(${humidityLimitMin})")
        	break;	
         
        case "report":
            online = "true"
            devstate = object.data.state
            lowBattery = object.data.alarm.lowBattery.toString()                            
            lowTemp = object.data.alarm.lowTemp.toString()
            highTemp = object.data.alarm.highTemp.toString()
            battery = parent.batterylevel(object.data.battery) 
            batteryType = parent.batteryType              //X3 
            recordInterval =  object.data.recordInterval  //X3
            alertInterval = object.data.interval
            tempCorrection = object.data.tempCorrection
            temperature = object.data.temperature
            tempLimitMax = object.data.tempLimit.max
            tempLimitMin = object.data.tempLimit.min
            firmware = object.data.version.toUpperCase()   
            rssi = object.data.loraInfo.signal

            humidity = object.data.humidity
            lowHumidity = object.data.alarm.lowHumidity.toString()       
            highHumidity = object.data.alarm.highHumidity.toString() 
            humidityLimitMax = object.data.humidityLimit.max
            humidityLimitMin = object.data.humidityLimit.min
            humidityCorrection = object.data.humidityCorrection
        
            logDebug("Report Event Reported: Temperature(${temperature}), Temp Limit Max(${tempLimitMax}), Temp Limit Min(${tempLimitMin})")
            
            temperature =  parent.convertTemperature(temperature,state.temperatureScale)
            tempLimitMax = parent.convertTemperature(tempLimitMax,state.temperatureScale)
            tempLimitMin = parent.convertTemperature(tempLimitMin,state.temperatureScale)

            logDebug("Report Event Converted: Temperature(${temperature}), Temp Limit Max(${tempLimitMax}), Temp Limit Min(${tempLimitMin})")

            rememberState("online",online)
            fmtSignal(rssi)  
            rememberState("lowBattery",lowBattery)
            rememberState("battery", battery, "%")   
            rememberState("alertInterval",alertInterval)
            rememberState("state",devstate)
            rememberState("tempCorrection",tempCorrection)
            rememberState("tempLimitMax",tempLimitMax)
            rememberState("tempLimitMin",tempLimitMin)
            rememberState("temperature", temperature, "°".plus(state.temperatureScale))
            rememberState("firmware",firmware)
            
            rememberState("humidity",humidity,"%rh")
            rememberState("humidityCorrection",humidityCorrection)
            rememberState("humidityLimitMax",humidityLimitMax)
            rememberState("humidityLimitMin",humidityLimitMin)

            alarmState(temperature,tempLimitMin,tempLimitMax,humidity,humidityLimitMin,humidityLimitMax)
            
            setHighLow(temperature)
         
            logDebug("Device State: online(${online}), " +
                     "Firmware(${firmware}), " +
                     "RSSI(${rssi}), " +
                     "Low Battery(${lowBattery}), " +
                     "Low Temp:(${lowTemp}), " +
                     "Hight Temp(${highTemp}), " +
                     "Battery(${battery}), " +
                     "Battery Type(${batteryType}), " +
                     "Record Interval(${battery}), " +
                     "Alert Interval(${alertInterval}), " +
                     "State(${devstate}), " + 
                     "Temp Correction(${tempCorrection}), " + 
                     "Temperature(${temperature}), " +
                     "Temp Limit Max(${tempLimitMax}), " + 
                     "Temp Limit Min(${tempLimitMin}), " +
                     "Humidity(${humidity}), " +
                     "Low Humidity(${lowHumidity}), " +
                     "High Humidity(${highHumidity}), " +
                     "Humidity Limit Max(${humidityLimitMax}), " +
                     "Humidity Limit Min(${humidityLimitMin}), " +
                     "Humidity Correction(${humidityCorrection})")  
            break;	 
                
		case "alert":
            devstate = object.data.state
            lowBattery = object.data.alarm.lowBattery.toString()                             
            lowTemp = object.data.alarm.lowTemp.toString()
            highTemp = object.data.alarm.highTemp.toString()
            battery = parent.batterylevel(object.data.battery) 
            temperature = object.data.temperature
            firmware = object.data.version.toUpperCase()           
            rssi = object.data.loraInfo.signal    
        
            temperature = parent.convertTemperature(temperature,state.temperatureScale)
                        
            lowHumidity = object.data.alarm.lowHumidity.toString()       
            highHumidity = object.data.alarm.highHumidity.toString()
            humidity = object.data.humidity            

            rememberState("online","true")   
            rememberState("lowBattery",lowBattery)  
            rememberState("lowTemp",lowTemp)        
            rememberState("highTemp",highTemp)            
            rememberState("battery", battery, "%")    
            rememberState("temperature", temperature, "°".plus(state.temperatureScale))  
            rememberState("firmware",firmware)        
            fmtSignal(rssi) 
        
            rememberState("lowHumidity",lowHumidity)
            rememberState("highHumidity", highHumidity)
            rememberState("humidity",humidity,"%rh")
        
            alarmState(temperature,state.tempLimitMin,state.tempLimitMax,humidity,state.humidityLimitMin,state.humidityLimitMax)
        
            setHighLow(temperature)
               
            logDebug("State(${devstate}), " +
                     "Firmware(${firmware}), " +
                     "RSSI(${rssi}), " +            
                     "Low Battery(${lowBattery}), " +
                     "Low Temp:(${lowTemp}), " +
                     "Hight Temp(${highTemp}), " +   
                     "Battery(${battery}), " +
                     "Temperature(${temperature}), " +
                     "Low Humidity(${lowHumidity}), " +
                     "High Humidity(${highHumidity}), " +
                     "Humidity(${humidity})")      
            break;
                
		default:
            log.error "Undefined data source ($source)"            
			break;
	    } 
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
		case "setAlarm":            
            def alertInterval = object.data.interval    
            def tempLimitMax = object.data.tempLimit.max
            def tempLimitMin = object.data.tempLimit.min
            def rssi = object.data.loraInfo.signal

            tempLimitMax = parent.convertTemperature(tempLimitMax,state.temperatureScale)
            tempLimitMin = parent.convertTemperature(tempLimitMin,state.temperatureScale)

            def humidityLimitMax = object.data.humidityLimit.max
            def humidityLimitMin = object.data.humidityLimit.min 
          
            logDebug("setAlarm: Alert Interval(${alertInterval}), " +
                     "Temp Limit Max(${tempLimitMax}), " + 
                     "Temp Limit Min(${tempLimitMin}), " + 
                     "Humidity Limit Max(${humidityLimitMax}), " + 
                     "Humidity Limit Min(${humidityLimitMin}), " +            
                     "RSSI(${rssi})")
            
            rememberState("alertInterval",alertInterval)
            rememberState("tempLimitMax",tempLimitMax)
            rememberState("tempLimitMin",tempLimitMin)
            rememberState("humidityLimitMax",humidityLimitMax)
            rememberState("humidityLimitMin",humidityLimitMin)
            fmtSignal(rssi)            
			break;	
         
        case "Alert":                            
            parseDevice(object,"alert") 
            break;	 

        case "setCorrection":            
            tempCorrection = object.data.tempCorrection
            rememberState("tempCorrection",tempCorrection)
            humidityCorrection = object.data.humidityCorrection
            rememberState("humidityCorrection",humidityCorrection)
			break;

		case "Report":
        case "getState":    
            parseDevice(object,"report")
			break;	
            
        case "DataRecord":            // X3 Sensor
            temperature = object.data.records.temperature[0]
            humidity = object.data.records.humidity[0]
            logDebug("DataRecord: Temperature(${temperature}), Humidity(${humidity})")
                     
            temperature =  parent.convertTemperature(temperature,state.temperatureScale)
            
            setHighLow(temperature)
                     
            logDebug("DataRecord: Converted Temperature(${temperature})")
            
            rememberState("temperature", temperature, "°".plus(state.temperatureScale))
            rememberState("humidity",humidity,"%rh")
			break;    
            
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }
}

def alarmState(temperature,tempLimitMin,tempLimitMax,humidity,humidityLimitMin,humidityLimitMax) { // Override Alarms - not correct unless error reporting interval is set  
    def devstate = "normal"
    if (temperature < tempLimitMin) {
        rememberState("lowTemp","true")        
        devstate = "alert" 
    } else {
        rememberState("lowTemp","false")        
    }
            
    if (temperature > tempLimitMax) {    
        rememberState("highTemp","true")
        devstate = "alert" 
    } else {
        rememberState("highTemp","false")
    }
         
    if (humidity < humidityLimitMin) {
        rememberState("lowHumidity","true")
        devstate = "alert"  
    } else {
        rememberState("lowHumidity", "false")
    }
        
    if (humidity > humidityLimitMax) {        
        rememberState("highHumidity", "true")
        devstate = "alert" 
    } else {
        rememberState("highHumidity", "false")
    }
    
    if (devstate == "alert") {        
        rememberState("alarm", "both")
    } else {
        rememberState("alarm", "off")
    }
    
    rememberState("state",devstate)
}

def reset(){       
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("firmware")
    state.remove("lowBattery")
    state.remove("lowTemp")
    state.remove("highTemp")
    state.remove("battery")
    state.remove("rssi")
    state.remove("signal")
    state.remove("state")
    state.remove("tempCorrection")
    state.remove("tempLimitMax")
    state.remove("tempLimitMin")
    state.remove("temperature")
    state.remove("mode")
    state.remove("alertInterval")
    state.remove("alarm")

    rememberState("temperatureScale", parent.temperatureScale)
    
    state.remove("humidity")
    state.remove("lowHumidity")
    state.remove("highHumidity")
    state.remove("humidityCorrection")
    state.remove("humidityLimitMax")
    state.remove("humidityLimitMin")    
    
    state.remove("currentDay")    
    state.remove("highDay")    
    state.remove("lowDay")    
    state.remove("highest")
    state.remove("lowest")
    state.remove("highestDate")
    state.remove("lowestDate")  

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