/***
 *  YoLink™ Temperature Sensor (YS8004-UC)
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
 */

import groovy.json.JsonSlurper
import java.math.RoundingMode;

def clientVersion() {return "01.00.00"}

preferences {
    input title: "Driver Version", description: "YoLink™ Temperature Sensor (YS8004-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
	input title: "Please donate", description: "Donations allow me to purchase more YoLink devices for development. Copy and Paste the following into your browser: https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink Temperature Sensor Device", namespace: "srbarcus", author: "Steven Barcus") {     	
		capability "Polling"				
		capability "Battery"
        capability "TemperatureMeasurement"       
                                      
        command "debug", ['boolean']
        command "connect"                       // Attempt to establish MQTT connection
        command "reset"                  
              
        attribute "API", "String" 
        attribute "online", "String"
        attribute "firmware", "String"  
        attribute "reportAt", "String"
        attribute "signal", "String" 
        attribute "battery", "String"  
        attribute "batteryType", "String" 
        attribute "lastResponse", "String" 
        
        attribute "lowBattery", "String"  
        attribute "lowTemp", "String"  
        attribute "highTemp", "String"        
        attribute "temperatureScale", "String"   
        attribute "state", "String"   
        attribute "tempCorrection", "String"   
        attribute "tempLimitMax", "String" 
        attribute "tempLimitMin", "String" 
        attribute "temperature", "String"                         
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
            request.put("method", "THSensor.getState")                   
            request.put("targetDevice", "${state.devId}") 
            request.put("token", "${state.token}") 
        
        def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("getDevicestate()> pollAPI() response: ${object}")     
            
            if (successful(object)) {                
                parseDevice(object,"devicestate")                     
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

def parseDevice(object, source) {
    logDebug("parseDevice() Source: ${source}")
    
    def online
    def reportAt    
    def lowBattery
    def lowTemp
    def highTemp    
    def battery 
    def batteryType
    def alertInterval
    def temperatureScale 
    def devstate
    def tempCorrection
    def tempLimitMax 
    def tempLimitMin
    def temperature
    def firmware
    def signal    
    
    switch(source) {
		case "devicestate":      
            online = object.data.online     
            devstate = object.data.state.state
            lowBattery = object.data.state.alarm.lowBattery                             
            lowTemp = object.data.state.alarm.lowTemp
            highTemp = object.data.state.alarm.highTemp                         
            battery = parent.batterylevel(object.data.state.battery) 
            batteryType = object.data.state.batteryType
            alertInterval = object.data.state.interval
            temperatureScale = object.data.state.mode.toUpperCase()            
            tempCorrection = object.data.state.tempCorrection
            tempLimitMax = object.data.state.tempLimit.max
            tempLimitMin = object.data.state.tempLimit.min 
            temperature = object.data.state.temperature  
            firmware = object.data.state.version   
            reportAt = object.data.reportAt     
        
            temperature = parent.convertTemperature(temperature)   
            tempLimitMax = parent.convertTemperature(tempLimitMax)
            tempLimitMin = parent.convertTemperature(tempLimitMin)
       
            rememberState("online",online)    
            
            rememberState("reportAt",reportAt)                   
            rememberState("lowBattery",lowBattery)  
        
            alarmState(temperature,tempLimitMin,tempLimitMax)

            rememberState("battery",battery)    
            rememberState("batteryType",batteryType)    
            rememberState("alertInterval",alertInterval)
            rememberState("temperatureScale",temperatureScale)            
            rememberState("tempCorrection",tempCorrection)
            rememberState("tempLimitMax",tempLimitMax)
            rememberState("tempLimitMin",tempLimitMin)
            rememberState("temperature",temperature) 
            rememberState("firmware",firmware)     
         
            logDebug("Device State: online(${online}), " +
                     "State(${devstate}), " + 
                     "Report At(${reportAt}), " +
                     "Firmware(${firmware}), " +
                     "Low Battery(${lowBattery}), " +
                     "Low Temp:(${lowTemp}), " +
                     "Hight Temp(${highTemp}), " +                        
                     "Battery(${battery}), " +
                     "Battery Type(${batteryType}), " + 
                     "Alert Interval(${alertInterval}), " + 
                     "Temperature Scale(${temperatureScale}), " +                      
                     "Temp Correction(${tempCorrection}), " + 
                     "Temp Limit Max(${tempLimitMax}), " + 
                     "Temp Limit Min(${tempLimitMin}), " +   
                     "Temperature(${temperature})")
        	break;	
         
        case "report":                 
            online = "true"        
            devstate = "normal"     
            lowBattery = object.data.alarm.lowBattery                             
            lowTemp = object.data.alarm.lowTemp
            highTemp = object.data.alarm.highTemp             
            battery = parent.batterylevel(object.data.battery) 
            batteryType = object.data.batteryType
            alertInterval = object.data.interval
            temperatureScale = object.data.mode.toUpperCase()
            
            tempCorrection = object.data.tempCorrection
            tempLimitMax = object.data.tempLimit.max
            tempLimitMin = object.data.tempLimit.min 
            temperature = object.data.temperature  
            firmware = object.data.version   
            signal = object.data.loraInfo.signal
        
            temperature = parent.convertTemperature(temperature)   
            tempLimitMax = parent.convertTemperature(tempLimitMax)
            tempLimitMin = parent.convertTemperature(tempLimitMin)
       
            rememberState("online",online)       
            rememberState("signal",signal)    
            rememberState("lowBattery",lowBattery)  
        
            alarmState(temperature,tempLimitMin,tempLimitMax)
                    
            rememberState("battery",battery)    
            rememberState("batteryType",batteryType)    
            rememberState("alertInterval",alertInterval)
            rememberState("temperatureScale",temperatureScale)
            rememberState("state",devstate)
            rememberState("tempCorrection",tempCorrection)
            rememberState("tempLimitMax",tempLimitMax)
            rememberState("tempLimitMin",tempLimitMin)
            rememberState("temperature",temperature) 
            rememberState("firmware",firmware)     
         
            logDebug("Device State: online(${online}), " +
                     "Firmware(${firmware}), " +
                     "Low Battery(${lowBattery}), " +
                     "Low Temp:(${lowTemp}), " +
                     "Hight Temp(${highTemp}), " +                        
                     "Battery(${battery}), " +
                     "Battery Type(${batteryType}), " + 
                     "Alert Interval(${alertInterval}), " + 
                     "Temperature Scale(${temperatureScale}), " + 
                     "State(${devstate}), " + 
                     "Temp Correction(${tempCorrection}), " + 
                     "Temp Limit Max(${tempLimitMax}), " + 
                     "Temp Limit Min(${tempLimitMin}), " +   
                     "Temperature(${temperature})")
    
            break;	 
                
		case "alert":
            devstate = object.data.state.state
            lowBattery = object.data.alarm.lowBattery                             
            lowTemp = object.data.alarm.lowTemp
            highTemp = object.data.alarm.highTemp                    
            battery = parent.batterylevel(object.data.battery) 
            temperatureScale = object.data.mode.toUpperCase()
            alertInterval = object.data.interval 
            temperature = parent.convertTemperature(object.data.temperature)            
            firmware = object.data.version           
            signal = object.data.loraInfo.signal    
          
            rememberState("online","true")   
            rememberState("state",devstate)               
            rememberState("lowBattery",lowBattery)  
            rememberState("lowTemp",lowTemp)        
            rememberState("highTemp",highTemp)            
            rememberState("battery",battery)    
            rememberState("temperatureScale",temperatureScale)            
            rememberState("alertInterval",alertInterval)
            rememberState("temperature",temperature)                     
            rememberState("firmware",firmware)        
            rememberState("signal",signal)        
               
            logDebug("State(${devstate}), " +
                     "Firmware(${firmware}), " +
                     "Signal(${signal}), " +            
                     "Low Battery(${lowBattery}), " +
                     "Low Temp:(${lowTemp}), " +
                     "Hight Temp(${highTemp}), " +   
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
        def event = object.event.replace("THSensor.","")
        logDebug("Received Message Type: ${event} for: $name")
        
        switch(event) {
		case "setAlarm":            
            def alertInterval = object.data.interval    
            def tempLimitMax = object.data.tempLimit.max
            def tempLimitMin = object.data.tempLimit.min 
    
            def signal = object.data.loraInfo.signal            
    
            if (temperatureScale == "F") {      
                tempLimitMax = parent.celsiustofahrenheit(tempLimitMax)
                tempLimitMin = parent.celsiustofahrenheit(tempLimitMin)            
            }   
          
            logDebug("setAlarm: Alert Interval(${alertInterval}), " +
                     "Temp Limit Max(${tempLimitMax}), " + 
                     "Temp Limit Min(${tempLimitMin}), " + 
                     "Signal(${signal})")
            
            rememberState("alertInterval",alertInterval)
            rememberState("tempLimitMax",tempLimitMax)
            rememberState("tempLimitMin",tempLimitMin)
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

def alarmState(temperature,tempLimitMin,tempLimitMax) { // Override Alarms - not correct unless error reporting interval is set  
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
         
     rememberState("state",devstate)
}

def reset(){       
    state.debug = false  
    state.remove("API")
    state.remove("firmware")
    state.remove("reportAt")    
    state.remove("lowBattery")
    state.remove("lowTemp")
    state.remove("highTemp")
    state.remove("battery")
    state.remove("batteryType")
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

def round(double strValue, int decimalPlace) {
    return new BigDecimal(strValue).setScale(decimalPlace, RoundingMode.HALF_UP) //.stripTrailingZeros().toPlainString();
  }

def lastResponse(value) {
   sendEvent(name:"lastResponse", value: "$value", isStateChange:true)   
}

def rememberState(name,value) {
   if (state."$name" != value) {
     state."$name" = value   
     sendEvent(name:"$name", value: "$value", isStateChange:true)
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