/***
 *  YoLink™ Temperature Humidity Sensor (YS8003-UC)
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
 *  1.0.1: Removed undocumented parameters, "Period" and "Code"
 *         - Corrected event parsing causing "null" signal 
 *         - Corrected errors in poll() 
 *  1.0.2: Corrected ServiceSetup error
 *  1.0.3: Fixed clientVersion()
 *  1.0.4: Fixed parsing error: groovy.lang.MissingPropertyException: No such property: state for class: java.lang.String on line nnn (method parse)
 *  1.0.5: Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 *         -  Removed superfluous code, correct attribute types, correct attributes to match standards, correct data to match attribute, remove math import
 */

import groovy.json.JsonSlurper

def clientVersion() {return "1.0.5"}

preferences {
    input title: "Driver Version", description: "YoLink™ Temperature Humidity Sensor (YS8003-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
	input title: "Please donate", description: "Donations allow me to purchase more YoLink devices for development. Copy and Paste the following into your browser: https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink THSensor Device", namespace: "srbarcus", author: "Steven Barcus") {     	
		capability "Polling"				
		capability "Battery"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]]  
        command "connect"                       // Attempt to establish MQTT connection
        command "reset"                  
              
        attribute "API", "String" 
        attribute "online", "String"
        attribute "firmware", "String"  
        attribute "reportAt", "String"
        attribute "signal", "String" 
        attribute "lastResponse", "String" 
        
        attribute "lowBattery", "String"  
        attribute "lowTemp", "String"  
        attribute "highTemp", "String"  
        attribute "humidityCorrection", "Number" 
        attribute "humidityLimitMax", "Number"   
        attribute "humidityLimitMin", "Number"   
        attribute "temperatureScale", "String"   
        attribute "state", "String"   
        attribute "tempCorrection", "Number"   
        attribute "tempLimitMax", "Number" 
        attribute "tempLimitMin", "Number" 
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
    def lowHumidity
    def highHumidity
    def battery
    def humidity
    def humidityCorrection
    def humidityLimitMax
    def humidityLimitMin
    def alertInterval
    def temperatureScale 
    def devstate
    def tempCorrection
    def tempLimitMax 
    def tempLimitMin
    def temperature
    def firmware   
    def reportAt
    def signal
    
    switch(source) {
   		case "devicestate":    
            online             = object.data.online.toString()                                  
            lowBattery         = object.data.state.alarm.lowBattery.toString()                             
            lowTemp            = object.data.state.alarm.lowTemp.toString()
            highTemp           = object.data.state.alarm.highTemp.toString() 
            lowHumidity        = object.data.state.alarm.lowHumidity.toString()       
            highHumidity       = object.data.state.alarm.highHumidity.toString()               
            battery            = object.data.state.battery 
            humidity           = object.data.state.humidity 
            humidityCorrection = object.data.state.humidityCorrection
            humidityLimitMax   = object.data.state.humidityLimit.max
            humidityLimitMin   = object.data.state.humidityLimit.min
            alertInterval      = object.data.state.interval
            temperatureScale   = object.data.state.mode.toUpperCase()
            devstate           = object.data.state.state
            tempCorrection     = object.data.state.tempCorrection
            tempLimitMax       = object.data.state.tempLimit.max
            tempLimitMin       = object.data.state.tempLimit.min 
            temperature        = object.data.state.temperature  
            firmware           = object.data.state.version   
            reportAt           = object.data.reportAt    
        
            logDebug("Device State: online(${online}), " +
                     "State(${devstate}), " + 
                     "Report At(${reportAt}), " +
                     "Firmware(${firmware}), " +
                     "Low Battery(${lowBattery}), " +
                     "Low Temp:(${lowTemp}), " +
                     "Hight Temp(${highTemp}), " +   
                     "Low Humidity(${lowHumidity}), " +
                     "High Humidity(${highHumidity}), " +
                     "Battery(${battery}), " + 
                     "Humidity(${humidity}), " + 
                     "Humidity Correction(${humidityCorrection}), " + 
                     "Humidity Limit Max(${humidityLimitMax}), " + 
                     "Humidity Limit Min(${humidityLimitMin}), " + 
                     "Alert Interval(${alertInterval}), " + 
                     "Temperature Scale(${temperatureScale}), " +     
                     "Temp Correction(${tempCorrection}), " + 
                     "Temp Limit Max(${tempLimitMax}), " + 
                     "Temp Limit Min(${tempLimitMin}), " +   
                     "Temperature(${temperature})")       
        
            battery = parent.batterylevel(battery) 
          
            humidity = (humidity.toDouble() + humidityCorrection.toDouble()).round(1)
                  
            temperature =  parent.convertTemperature(temperature)
            tempLimitMax = parent.convertTemperature(tempLimitMax)
            tempLimitMin = parent.convertTemperature(tempLimitMin)
                               
            logDebug("Device State Adjusted: Temp Limit Max(${tempLimitMax}), Temp Limit Min(${tempLimitMin}), Temperature(${temperature})")
        
            rememberState("online",online)    
            
            rememberState("reportAt",reportAt)                   
            rememberState("lowBattery",lowBattery)  
        
            alarmState(temperature,tempLimitMin,tempLimitMax,humidity,humidityLimitMin,humidityLimitMax)
        
            rememberState("battery",battery)    
            rememberState("batteryType",batteryType)    
            rememberState("alertInterval",alertInterval)
            rememberState("temperatureScale",temperatureScale)            
            rememberState("tempCorrection",tempCorrection)
            rememberState("tempLimitMax",tempLimitMax)
            rememberState("tempLimitMin",tempLimitMin)        
            rememberState("temperature",temperature,temperatureScale)         
            rememberState("humidity",humidity)
            rememberState("humidityCorrection",humidityCorrection)
            rememberState("humidityLimitMax",humidityLimitMax)
            rememberState("humidityLimitMin",humidityLimitMin)
            rememberState("firmware",firmware) 
               
        	break;	
         
        case "report":                 
            devstate = object.data.state                            //1.0.4
            lowBattery = object.data.alarm.lowBattery.toString()                            
            lowTemp = object.data.alarm.lowTemp.toString()
            highTemp = object.data.alarm.highTemp.toString() 
            lowHumidity = object.data.alarm.lowHumidity.toString()       
            highHumidity = object.data.alarm.highHumidity.toString()   
            battery = parent.batterylevel(object.data.battery) 
            temperatureScale = object.data.mode.toUpperCase()
            alertInterval = object.data.interval 
            temperature = object.data.temperature           
            humidity = object.data.humidity
            tempLimitMax = object.data.tempLimit.max
            tempLimitMin = object.data.tempLimit.min 
            humidityLimitMax = object.data.humidityLimit.max
            humidityLimitMin = object.data.humidityLimit.min
            tempCorrection = object.data.tempCorrection
            humidityCorrection = object.data.humidityCorrection
            firmware = object.data.version   
            signal = object.data.loraInfo.signal    
        
            temperature =  parent.convertTemperature(temperature)
            tempLimitMax = parent.convertTemperature(tempLimitMax)
            tempLimitMin = parent.convertTemperature(tempLimitMin)
        
            rememberState("online","true")    
            
            rememberState("lowBattery",lowBattery)  
        
            alarmState(temperature,tempLimitMin,tempLimitMax,humidity,humidityLimitMin,humidityLimitMax)
        
            rememberState("battery",battery)    
            rememberState("alertInterval",alertInterval)
            rememberState("temperatureScale",temperatureScale)            
            rememberState("tempCorrection",tempCorrection)
            rememberState("tempLimitMax",tempLimitMax)
            rememberState("tempLimitMin",tempLimitMin)        
            rememberState("temperature",temperature,temperatureScale)         
            rememberState("humidity",humidity)
            rememberState("humidityCorrection",humidityCorrection)
            rememberState("humidityLimitMax",humidityLimitMax)
            rememberState("humidityLimitMin",humidityLimitMin)
            rememberState("firmware",firmware) 
            rememberState("signal",firmware) 
         
            logDebug("State(${devstate}), " +
                     "Firmware(${firmware}), " +
                     "Signal(${signal}), " +            
                     "Low Battery(${lowBattery}), " +
                     "Low Temp:(${lowTemp}), " +
                     "Hight Temp(${highTemp}), " +   
                     "Low Humidity(${lowHumidity}), " +
                     "High Humidity(${highHumidity}), " +
                     "Battery(${battery}), " + 
                     "Temperature Scale(${temperatureScale}), " + 
                     "Alert Interval(${alertInterval}), " +             
                     "Temperature(${temperature}), " + 
                     "Humidity(${humidity}), " + 
                     "Temp Limit Max(${tempLimitMax}), " + 
                     "Temp Limit Min(${tempLimitMin}), " +   
                     "Humidity Limit Max(${humidityLimitMax}), " + 
                     "Humidity Limit Min(${humidityLimitMin}), " + 
                     "Temp Correction(${tempCorrection}), " +         
                     "Humidity Correction(${humidityCorrection})")         
            break;	 
                
		case "alert":
            devstate = object.data.state                      //1.0.4
            lowBattery = object.data.alarm.lowBattery.toString()                             
            lowTemp = object.data.alarm.lowTemp.toString()
            highTemp = object.data.alarm.highTemp.toString() 
            lowHumidity = object.data.alarm.lowHumidity.toString()       
            highHumidity = object.data.alarm.highHumidity.toString()               
            battery = parent.batterylevel(object.data.battery) 
            temperatureScale = object.data.mode.toUpperCase()
            alertInterval = object.data.interval 
            temperature = parent.convertTemperature(temperature)
            humidity = object.data.humidity
            firmware = object.data.version           
            signal = object.data.loraInfo.signal    
          
            rememberState("online","true")   
            rememberState("state",devstate)               
            rememberState("lowBattery",lowBattery)  
            rememberState("lowTemp",lowTemp)        
            rememberState("highTemp",highTemp)
            rememberState("lowHumidity",lowHumidity)
            rememberState("highHumidity", highHumidity)
            rememberState("battery",battery)    
            rememberState("temperatureScale",temperatureScale)            
            rememberState("alertInterval",alertInterval)
            rememberState("temperature",temperature,temperatureScale)         
            rememberState("humidity",humidity)
            rememberState("firmware",firmware)        
            rememberState("signal",signal)        
               
            logDebug("State(${devstate}), " +
                     "Firmware(${firmware}), " +
                     "Signal(${signal}), " +            
                     "Low Battery(${lowBattery}), " +
                     "Low Temp:(${lowTemp}), " +
                     "Hight Temp(${highTemp}), " +   
                     "Low Humidity(${lowHumidity}), " +
                     "High Humidity(${highHumidity}), " +
                     "Battery(${battery}), " + 
                     "Temperature Scale(${temperatureScale}), " + 
                     "Alert Interval(${alertInterval}), " +             
                     "Temperature(${temperature}), " + 
                     "Humidity(${humidity})")                             
			break;	
                
		default:
            log.error "Undefined data source ($source)"            
			break;
	    } 
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
		case "setAlarm":            
            def alertInterval = object.data.interval    
            def tempLimitMax = object.data.tempLimit.max
            def tempLimitMin = object.data.tempLimit.min 
            def humidityLimitMax = object.data.humidityLimit.max
            def humidityLimitMin = object.data.humidityLimit.min    
            def signal = object.data.loraInfo.signal            
    
            if (temperatureScale == "F") {      
                tempLimitMax = parent.celsiustofahrenheit(tempLimitMax)
                tempLimitMin = parent.celsiustofahrenheit(tempLimitMin)            
            }   
          
            logDebug("setAlarm: Alert Interval(${alertInterval}), " +
                     "Temp Limit Max(${tempLimitMax}), " + 
                     "Temp Limit Min(${tempLimitMin}), " + 
                     "Humidity Limit Max(${humidityLimitMax}), " + 
                     "Humidity Limit Min(${humidityLimitMin}), " +            
                     "Signal(${signal})")
            
            rememberState("alertInterval",alertInterval)
            rememberState("tempLimitMax",tempLimitMax)
            rememberState("tempLimitMin",tempLimitMin)
            rememberState("humidityLimitMax",humidityLimitMax)
            rememberState("humidityLimitMin",humidityLimitMin)
            rememberState("signal",signal)
            
			break;	
         
        case "Alert":                            
            parseDevice(object,"alert") 
            break;	 
                
		case "Report":
            parseDevice(object,"report")
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
    
    rememberState("state",devstate)
}

def reset(){       
    state.remove("API")
    state.remove("firmware")
    state.remove("reportAt")    
    state.remove("lowBattery")
    state.remove("lowTemp")
    state.remove("highTemp")
    state.remove("lowHumidity")
    state.remove("highHumidity")
    state.remove("period")        //Remove, no longer supporting - was never defined in API
    state.remove("code")          //Remove, no longer supporting - was never defined in API 
    state.remove("battery")
    state.remove("humidity")
    state.remove("humidityCorrection")
    state.remove("humidityLimitMax")
    state.remove("humidityLimitMin")
    state.remove("temperatureScale")
    state.remove("state")
    state.remove("tempCorrection")
    state.remove("tempLimitMax")
    state.remove("tempLimitMin")
    state.remove("temperature")
    state.remove("online")
    state.remove("mode")       
      
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
   if (state.debug) {log.debug msg}
} 
