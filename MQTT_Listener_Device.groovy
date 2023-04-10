/***
 *  YoLink™ MQTT Listener Device
 *  © 2022, 2023 Steven Barcus. All rights reserved.
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
 *  2.0.1: Added temperatureScale(value) for service app compatabilty
 *  2.0.2: Correct MQTT message error reporting
 *  2.0.3: Reduce time returning from Poll()
 *  2.0.4: Added "driver" attribute and isSetup() for diagnostics
 *  2.0.5: Support diagnostics, correct various errors, make singleThreaded
 *  2.0.6: Copyright update and UI formatting
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.6"}
def copyright() {return "<br>© 2022, 2023 Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "YoLink™ MQTT Listener v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink MQTT Listener Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     	
		capability "Polling"				
        capability "Initialize"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]]
        command "connect"                       // Attempt to establish MQTT connection 
        command "reset"
        
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"          
        attribute "firmware", "String"
        attribute "MQTT", "String" 
        attribute "lastResponse", "String"
        }
   }

// Called by YoLinkService App on initial creation of a child Device
void ServiceSetup(Hubitat_dni,homeID,devname,devtype,devtoken,devId) {  //dev.ServiceSetup(Hubitat_dni,state.homeID,devname,devtype,devtoken,devId) 
	settings.trace=true	
    
    state.my_dni = Hubitat_dni      
    state.homeID = homeID    
    state.name = devname
    state.type = devtype
    state.token = devtoken
    rememberState("devId", devId)   
    	
    log.debug "ServiceSetup(Hubitat dni=${state.my_dni}, Home ID=${state.homeID}, Name=${state.name}, Type=${state.type}, Token=${state.token}, Device Id=${state.devId})"
	    
    connect()      //Establish MQTT connection to YoLink API 
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

def initialize() {
   log.trace "Device initializing. Establishing MQTT connection to YoLink API" 
   connect()           //Establish MQTT connection to YoLink API 
 }

def uninstalled() {
   interfaces.mqtt.disconnect() // Guarantee we're disconnected 
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def poll() {
  rememberState("driver", clientVersion())  
  def MQTT = interfaces.mqtt.isConnected()  
  rememberState("online",MQTT) 
  rememberState("driver", clientVersion())     
  logDebug("MQTT connection is ${MQTT}")  
  if (MQTT) {  
     rememberState("MQTT", "connected")     
  } else {   
     runIn(1,connect) //Establish MQTT connection to YoLink API
  }
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

def connect() {
    interfaces.mqtt.disconnect()              // Guarantee we're disconnected  
    
    def zigid = location.hub.zigbeeId
    establish_MQTT_connection(zigid)
 }


//>>>>>>>>>>>>>>>>>>>><<<<<<<<<<<<<<<<<<</
//>>>>>>>>>>>> MQTT ROUTINES <<<<<<<<<<<</
//>>>>>>>>>>>>>>>>>>>><<<<<<<<<<<<<<<<<<</

def establish_MQTT_connection(mqtt_ID) {
    parent.refreshAuthToken()
    def authToken = parent.AuthToken() 
      
    def MQTT = "disconnected"
    
    def topic = "yl-home/${state.homeID}/+/+"
   
    try {  	
       mqtt_ID =  "${mqtt_ID}_${state.homeID}"
        
       logDebug("Connecting to MQTT with ID '${mqtt_ID}', Topic:'${topic}, Token:'${authToken}")          
       interfaces.mqtt.connect("tcp://api.yosmart.com:8003","${mqtt_ID}",authToken,null)            
        
       logDebug("Subscribing to MQTT topic '${topic}'") 
       interfaces.mqtt.subscribe("${topic}", 0) 
         
       MQTT = "connected" 
        
       logDebug("MQTT connection to YoLink successful")
		
	  } catch (e) {	
          log.error ("establish_MQTT_connection() Exception: $e",)			
    }
        
    rememberState("MQTT", MQTT)    
    rememberState("online",interfaces.mqtt.isConnected())  
    lastResponse("API MQTT ${MQTT}")  
}    

def mqttClientStatus(String message) {                          
    logDebug("mqttClientStatus(${message})")

    if (message.startsWith("Error:")) {
        log.error "MQTT Error: ${message}"

        try {
            log.info "Disconnecting from MQTT"    
            interfaces.mqtt.disconnect() // Guarantee we're disconnected            
            if (state.MQTT != "disconnected") {sendEvent(name:"MQTT", value: "disconnected", isStateChange:true)}   
            state.MQTT = "disconnected"
        }
        catch (e) {
        } 
    }
}

def parse(message) {  //CALLED BY MQTT       
    def topic = interfaces.mqtt.parseMessage(message)
    logDebug("Passing MQTT topic: ${topic}")
    
    state.message = topic
    
    if (parent.passMQTT(topic)) {
        logDebug("MQTT message passed successfully")
    } else {
        log.error "MQTT message ${topic} - Failed passing to device driver"
    }    
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

def reset(){    
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("message") 
    state.remove("MQTT")    
    state.remove("firmware")
    state.remove("lastResponse")        
    
    rememberState("firmware","N/A")
    
    connect()
    
    log.warn "Device reset to default values"
}

def lastResponse(value) {
   sendEvent(name:"lastResponse", value: "$value", isStateChange:true)   
}

def logDebug(msg) {
   if (state.debug == "true") {log.debug msg}
}