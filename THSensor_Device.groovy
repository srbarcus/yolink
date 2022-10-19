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
 *  1.0.6: Fix syncing of Temperature scale with YoLink™ Device Service app
 *  1.0.7: Fix donation URL
 *  1.0.8: Added getSetup()
 *  2.0.0: Reengineer driver to use centralized MQTT listener due to new YoLink service restrictions 
 *  2.0.1: Multiple Fixes, add 'Alarm' capability (status only)
 *  2.0.2: Added 'Alarm' capability (status only)
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.2"}

preferences {
    input title: "Driver Version", description: "YoLink™ Temperature Humidity Sensor (YS8003-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Please donate", description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink THSensor Device", namespace: "srbarcus", author: "Steven Barcus") {     	
		capability "Polling"				
		capability "Battery"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "Alarm" //ENUM ["strobe", "off", "both", "siren"]
        
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]]  
        command "reset"                  
              
        attribute "online", "String"
        attribute "firmware", "String"  
        attribute "reportAt", "String"
        attribute "signal", "String"
        attribute "lastResponse", "String" 
        
        attribute "lowBattery", "String"  
        attribute "lowTemp", "String"  
        attribute "highTemp", "String"
        attribute "lowHumidity", "String"
        attribute "highHumidity", "String" 
        attribute "humidityCorrection", "String" 
        attribute "humidityLimitMax", "String"   
        attribute "humidityLimitMin", "String"   
        attribute "temperatureScale", "String"
        attribute "state", "String"
        attribute "tempCorrection", "String"
        attribute "tempLimitMax", "String"
        attribute "tempLimitMin", "String"
        attribute "alertInterval", "String"
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

def temperatureScale(value) {
    state.temperatureScale = value
 }

def debug(value) { 
   rememberState("debug",value)
   if (value) {
     log.info "Debugging enabled"
   } else {
     log.info "Debugging disabled"
   }    
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
    def alertInterval
    def temperatureScale 
    def devstate
    def tempCorrection
    def tempLimitMax 
    def tempLimitMin
    def temperature
    def firmware
    def signal
    def humidity
    def lowHumidity
    def highHumidity
    def humidityCorrection
    def humidityLimitMax
    def humidityLimitMin

    
    switch(source) {
		case "devicestate":
            online           = object.data.online.toString()
            devstate         = object.data.state.state
            lowBattery       = object.data.state.alarm.lowBattery.toString()
            lowTemp          = object.data.state.alarm.lowTemp.toString()
            highTemp         = object.data.state.alarm.highTemp.toString()
            battery          = parent.batterylevel(object.data.state.battery)
            alertInterval    = object.data.state.interval
            temperatureScale = object.data.state.mode.toUpperCase()
            tempCorrection   = object.data.state.tempCorrection
            tempLimitMax     = object.data.state.tempLimit.max
            tempLimitMin     = object.data.state.tempLimit.min 
            temperature      = object.data.state.temperature  
            firmware         = object.data.state.version   

            humidity           = object.data.state.humidity 
            humidityCorrection = object.data.state.humidityCorrection
            humidityLimitMax   = object.data.state.humidityLimit.max
            humidityLimitMin   = object.data.state.humidityLimit.min
            lowHumidity        = object.data.state.alarm.lowHumidity.toString()
            highHumidity       = object.data.state.alarm.highHumidity.toString()
            humidity = (humidity.toDouble() + humidityCorrection.toDouble()).round(1)
                  
            temperature =  parent.convertTemperature(temperature)
            temperature = (temperature.toDouble() + tempCorrection.toDouble()).round(1)
            tempLimitMax = parent.convertTemperature(tempLimitMax)
            tempLimitMin = parent.convertTemperature(tempLimitMin)

            logDebug("Device State Adjusted: Temp Limit Max(${tempLimitMax}), Temp Limit Min(${tempLimitMin}), Temperature(${temperature})")

            rememberState("online",online)    
            
            rememberState("reportAt",reportAt)                   
            rememberState("lowBattery",lowBattery)  
        
            alarmState(temperature,tempLimitMin,tempLimitMax,humidity,humidityLimitMin,humidityLimitMax)

            rememberState("battery",battery)    
            rememberState("alertInterval",alertInterval)
            rememberState("temperatureScale",temperatureScale)            
            rememberState("tempCorrection",tempCorrection)
            rememberState("tempLimitMax",tempLimitMax)
            rememberState("tempLimitMin",tempLimitMin)
            rememberState("temperature",temperature,temperatureScale)
            rememberState("firmware",firmware)

            rememberState("humidity",humidity)
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
                     "Temperature Scale(${temperatureScale}), " +
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
            alertInterval = object.data.interval
            temperatureScale = object.data.mode.toUpperCase()
            tempCorrection = object.data.tempCorrection
            temperature = object.data.temperature
            tempLimitMax = object.data.tempLimit.max
            tempLimitMin = object.data.tempLimit.min
            firmware = object.data.version   
            signal = object.data.loraInfo.signal

            humidity = object.data.humidity
            lowHumidity = object.data.alarm.lowHumidity.toString()       
            highHumidity = object.data.alarm.highHumidity.toString() 
            humidityLimitMax = object.data.humidityLimit.max
            humidityLimitMin = object.data.humidityLimit.min
            humidityCorrection = object.data.humidityCorrection
            humidity = (humidity.toDouble() + humidityCorrection.toDouble()).round(1)

            temperature = parent.convertTemperature(temperature)
            temperature = (temperature.toDouble() + tempCorrection.toDouble()).round(1)
            tempLimitMax = parent.convertTemperature(tempLimitMax)
            tempLimitMin = parent.convertTemperature(tempLimitMin)

            rememberState("online",online)
            rememberState("signal",signal)
            rememberState("lowBattery",lowBattery)
            rememberState("battery",battery)    
            rememberState("alertInterval",alertInterval)
            rememberState("temperatureScale",temperatureScale)
            rememberState("state",devstate)
            rememberState("tempCorrection",tempCorrection)
            rememberState("tempLimitMax",tempLimitMax)
            rememberState("tempLimitMin",tempLimitMin)
            rememberState("temperature",temperature,temperatureScale)
            rememberState("firmware",firmware)
            rememberState("signal",signal)
            
            rememberState("humidity",humidity)
            rememberState("humidityCorrection",humidityCorrection)
            rememberState("humidityLimitMax",humidityLimitMax)
            rememberState("humidityLimitMin",humidityLimitMin)

            alarmState(temperature,tempLimitMin,tempLimitMax,humidity,humidityLimitMin,humidityLimitMax)
         
            logDebug("Device State: online(${online}), " +
                     "Firmware(${firmware}), " +
                     "Signal(${signal}), " +
                     "Low Battery(${lowBattery}), " +
                     "Low Temp:(${lowTemp}), " +
                     "Hight Temp(${highTemp}), " +
                     "Battery(${battery}), " +
                     "Temperature Scale(${temperatureScale}), " + 
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
            temperatureScale = object.data.mode.toUpperCase()
            temperature = object.data.temperature
            firmware = object.data.version           
            signal = object.data.loraInfo.signal    
        
            temperature = parent.convertTemperature(temperature)
            temperature = (temperature.toDouble() + state.tempCorrection.toDouble()).round(1)
            
            lowHumidity = object.data.alarm.lowHumidity.toString()       
            highHumidity = object.data.alarm.highHumidity.toString()
            humidity = object.data.humidity        
            humidity = (humidity.toDouble() + state.humidityCorrection.toDouble()).round(1)

        
            alarmState(temperature,state.tempLimitMin,state.tempLimitMax,humidity,state.humidityLimitMin,state.humidityLimitMax)

            rememberState("online","true")   
            rememberState("lowBattery",lowBattery)  
            rememberState("lowTemp",lowTemp)        
            rememberState("highTemp",highTemp)            
            rememberState("battery",battery)    
            rememberState("temperatureScale",temperatureScale)
            rememberState("temperature",temperature,temperatureScale)  
            rememberState("firmware",firmware)        
            rememberState("signal",signal)
        
            rememberState("lowHumidity",lowHumidity)
            rememberState("highHumidity", highHumidity)
            rememberState("humidity",humidity)
               
            logDebug("State(${devstate}), " +
                     "Firmware(${firmware}), " +
                     "Signal(${signal}), " +            
                     "Low Battery(${lowBattery}), " +
                     "Low Temp:(${lowTemp}), " +
                     "Hight Temp(${highTemp}), " +   
                     "Battery(${battery}), " + 
                     "Temperature Scale(${temperatureScale}), " + 
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
            def signal = object.data.loraInfo.signal

            tempLimitMax = parent.convertTemperature(tempLimitMax)
            tempLimitMin = parent.convertTemperature(tempLimitMin)

            def humidityLimitMax = object.data.humidityLimit.max
            def humidityLimitMin = object.data.humidityLimit.min 
          
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

        case "setCorrection":            
            tempCorrection = object.data.tempCorrection
            rememberState("tempCorrection",tempCorrection)
            humidityCorrection = object.data.humidityCorrection
            rememberState("humidityCorrection",humidityCorrection)
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
    
    if (devstate == "alert") {        
        rememberState("alarm", "both")
    } else {
        rememberState("alarm", "off")
    }
    
    rememberState("state",devstate)
}

def reset(){       
    state.remove("firmware")
    state.remove("reportAt")    
    state.remove("lowBattery")
    state.remove("lowTemp")
    state.remove("highTemp")
    state.remove("battery")
    state.remove("temperatureScale")
    state.remove("state")
    state.remove("tempCorrection")
    state.remove("tempLimitMax")
    state.remove("tempLimitMin")
    state.remove("temperature")
    state.remove("online")
    state.remove("mode")
    state.remove("alertInterval")
    state.remove("alarm")
    
    state.remove("humidity")
    state.remove("lowHumidity")
    state.remove("highHumidity")
    state.remove("humidityCorrection")
    state.remove("humidityLimitMax")
    state.remove("humidityLimitMin")   
      
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
