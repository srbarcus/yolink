/***
 *  YoLink™ Hub (YS1603-UC)
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
 *  1.0.2: Add constraints to debug command
 *  1.0.3: def temperatureScale()
 *  1.0.4: Fix donation URL
 */

import groovy.json.JsonSlurper

def clientVersion() {return "1.0.4"}

preferences {
    input title: "Driver Version", description: "YoLink™ Hub (YS1603-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Please donate", description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

/* As of 03-06-2022, was not supported by API  
preferences {
	input("SSID", "text", title: "WiFi SSID", description:
		"The SSID of your wireless network", required: true)
	input("WiFiPassword", "text", title: "WiFi SSID", description:
		"The password of the wireless network with the SSID specified above", required: true)
}
*/

metadata {
    definition (name: "YoLink Hub Device", namespace: "srbarcus", author: "Steven Barcus") {     		
		capability "Polling"						
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]] 
        command "connect"                       // Attempt to establish MQTT connection
        command "reset" 
        
      //command "setWiFi"                       // As of 03-06-2022, was not supported by API    
        
        attribute "API", "String" 
        attribute "online", "String"
        attribute "firmware", "String"  
        attribute "signal", "String" 
        attribute "lastResponse", "String"
      //attribute "reportAt", "String"    
        
        attribute "wifi_ssid", "String"  
        attribute "wifi_enabled", "String"  
        attribute "wifi_ip", "String"  
        attribute "wifi_gateway", "String"  
        attribute "wifi_mask", "String"
        attribute "ethernet_enabled", "String"
        attribute "ethernet_ip", "String"
        attribute "ethernet_gateway", "String"
        attribute "ethernet_mask", "String"        
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

def temperatureScale(value) {}

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
            logDebug("getDevicestate(): pollAPI() response: ${object}")                          
         
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
    def firmware = object.data.version
    def wifi_ssid = object.data.wifi.ssid                    
    def wifi_enabled = object.data.wifi.enable                             
    def wifi_ip = object.data.wifi.ip  
    def wifi_gateway = object.data.wifi.gateway 
    def wifi_mask = object.data.wifi.mask       
    def ethernet_enabled = object.data.eth.enable
    def ethernet_ip = object.data.eth.ip
    def ethernet_gateway = object.data.eth.gateway
    def ethernet_mask = object.data.eth.mask                        
            
    logDebug("Hub Firmware Version (${firmware}), " +
             "WiFi SSID(${wifi_ssid}), " +
             "WiFi Enabled(${wifi_enabled}), " +
             "WiFi IP(${wifi_ip}), " +
             "WiFi Gateway(${wifi_gateway}), " +
             "WiFi Mask(${wifi_mask}), " +
             "Ethernet Enabled(${ethernet_enabled}), " +       
             "Ethernet IP(${ethernet_ip}), " +
             "Ethernet Gateway(${ethernet_gateway}), " +
             "Ethernet Mask(${ethernet_mask})")
               
     rememberState("firmware", firmware)
     rememberState("wifi_ssid", wifi_ssid)
     rememberState("wifi_enabled", wifi_enabled)
     rememberState("wifi_ip", wifi_ip)
     rememberState("wifi_gateway", wifi_gateway)
     rememberState("wifi_mask", wifi_mask)
     rememberState("ethernet_enabled", ethernet_enabled)
     rememberState("ethernet_ip", ethernet_ip)
     rememberState("ethernet_gateway", ethernet_gateway)
     rememberState("ethernet_mask", ethernet_mask)
    
     rememberState("online", "true")         
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
        log.debug "Received Message Type: ${event} for: $name"
        
        switch(event) {
		case "Alert":          
            def swState = object.data.state
            def alertType = object.data.alertType    
            def battery = parent.batterylevel(object.data.battery)    // Value = 0-4    
            def firmware = object.data.firmware    
            def signal = object.data.loraInfo.signal    
    
            log.debug "Parsed: DeviceId=$devId, Switch=$swState, Alert=$alertType, Battery=$battery, Firmware=$firmware, Signal=$signal"  
        
            if (state.swState != swState) {sendEvent(name:"switch", value: swState, isStateChange:true)}
            if (state.alertType != alertType) {sendEvent(name:"alertType", value: alertType, isStateChange:true)}
            if (state.battery != battery) {sendEvent(name:"battery", value: battery, isStateChange:true)}
            if (state.firmware != firmware) {sendEvent(name:"firmware", value: firmware, isStateChange:true)}
            if (state.signal != signal) {sendEvent(name:"signal", value: signal, isStateChange:true)}   
                
            state.swState = swState
            state.alertType = alertType    
            state.battery = battery
            state.firmware = firmware   
            state.signal = signal   
                                       
                
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }
}

/* Currently not supported by API as of 02/27/2022
void setWiFi() { 
   logDebug "setting WiFi: SSID=${settings.SSID}, Password=${settings.WiFiPassword}"

   def params = [:] 
   params.put("ssid", settings.SSID)    
   params.put("password", settings.WiFiPassword) 
    
   def request = [:] 
   request.put("method", "Hub.setWiFi")                
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("setWiFi()> pollAPI() response: ${object}")                                            
            return 							
                
	    } else { 			               
            logDebug("setWiFi() failed")	
        }     		
	} catch (e) {	
        log.error "setWiFi() exception: $e"
	} 
}   
*/

def reset(){          
    state.debug = false  
    state.remove("API")
    state.remove("firmware") 
    state.remove("swState")
    state.remove("door")
    state.remove("alertType")  
    state.remove("battery")     
    state.remove("signal")  
    state.remove("online")
    state.remove("reportAt")
    state.remove("alertInterval")
    state.remove("delay")           
    state.remove("openRemindDelay")
      
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
