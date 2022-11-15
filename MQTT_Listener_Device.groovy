/***
 *  YoLink™ MQTT Listener Device
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
 *  2.0.1: Added temperatureScale(value) for service app compatabilty
 *  2.0.2: Correct MQTT message error reporting
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.2"}

metadata {
    definition (name: "YoLink MQTT Listener Device", namespace: "srbarcus", author: "Steven Barcus") {     	
		capability "Polling"				
        capability "Initialize"
                                      
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["True", "False"]]]
        command "connect"                       // Attempt to establish MQTT connection 
        command "reset"

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
    state.devId = devId   
    	
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

def installed() {   
 }

def updated() {
 }

def initialize() {
   log.trace "Device initializing. Establishing MQTT connection to YoLink API" 
   connect()      //Establish MQTT connection to YoLink API 
 }

def uninstalled() {
   interfaces.mqtt.disconnect() // Guarantee we're disconnected 
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def poll() {
  def MQTT = interfaces.mqtt.isConnected()  
  logDebug("MQTT connection is ${MQTT}")  
  if (MQTT) {  
     rememberState("MQTT", "connected")     
  } else {    
     connect()      //Establish MQTT connection to YoLink API
  }
 }

def temperatureScale(value) {}

def debug(value) { 
   rememberState("debug",value)
   if (value) {
     log.info "Debugging enabled"
   } else {
     log.info "Debugging disabled"
   }    
}

def connect() {
    interfaces.mqtt.disconnect()              // Guarantee we're disconnected  
    establish_MQTT_connection(state.my_dni)
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
        log.error "MQTT message ${topic} - Failed to passing to device driver"
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
    state.debug = false  
    state.remove("message") 
    state.remove("MQTT")    
    state.remove("lastResponse")        
    
    connect()
    
    log.info "Device reset to default values"
}

def lastResponse(value) {
   sendEvent(name:"lastResponse", value: "$value", isStateChange:true)   
}

def logDebug(msg) {
   if (state.debug) {log.debug msg}
}
