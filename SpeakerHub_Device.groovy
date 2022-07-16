/***
 *  YoLink™ SpeakerHub (YS1604-UC)
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
 * 01.00.01: Fixed errors in poll()
 */

import groovy.json.JsonSlurper

def clientVersion() {return "01.00.01"}

preferences {
    input title: "Driver Version", description: "YoLink™ SpeakerHub (YS1604-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
	input title: "Please donate", description: "Donations allow me to purchase more YoLink devices for development. Copy and Paste the following into your browser: https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

/* As of 03-06-2022, was not supported by API  
preferences {
	input("SSID", "text", title: "WiFi SSID", description:
		"The SSID of your wireless network", required: true)
	input("WiFiPassword", "text", title: "WiFi SSID", description:
		"The password of the wireless network with the SSID specified above", required: true)
}
*/

// As of 06-23-2022, playing of custom sounds was not supported by the API  


metadata {
    definition (name: "YoLink SpeakerHub Device", namespace: "srbarcus", author: "Steven Barcus") {     		
		capability "Polling"	
        capability "AudioNotification"
        capability "AudioVolume"
        
        
        command "setVolume", [[name:"volume",type:"ENUM", description:"Speaker volume", constraints:[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]]]    
        
        command "playText", [[name:"Text",type:"STRING", description:"Text to be played"], 
                            [name:"Volume",type:"ENUM", description:"Optional volume text is to be played at", optional:true,
                             constraints:[null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]]] 
        
        command "playTextAndResume", [[name:"Text",type:"STRING", description:"Text to be played"], 
                                      [name:"Volume",type:"ENUM", description:"Optional volume text is to be played at", optional:true,
                                       constraints:[null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]]] 
              
        command "playTextAndRestore", [[name:"Text",type:"STRING", description:"Text to be played"], 
                                       [name:"Volume",type:"ENUM", description:"Optional volume text is to be played at", optional:true,
                                        constraints:[null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]]] 
        
        
        command "playTrack", [[name:"Track",type:"ENUM", description:"Track to be played", constraints:["Emergency", "Alert", "Warn", "Warning", "Tip",
                                                                                                        "Fire", "Arpeggio", "Chime-down", "Chime-up",                                                                                                             
                                                                                                        "Warble", "Whistle", "Bing-Bong", "Hi-Lo", "Whoop"
                                                                                                        ]], 
                                                                                                        [name:"Volume",type:"ENUM", description:"Optional volume track is to be played at", optional:true,
                                                                                                         constraints:[null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]]] 
        
        command "playTrackAndResume", [[name:"Track",type:"ENUM", description:"Track to be played", constraints:["Emergency", "Alert", "Warn", "Warning", "Tip",
                                                                                                                 "Fire", "Arpeggio", "Chime-down", "Chime-up",                                                                                                             
                                                                                                                 "Warble", "Whistle", "Bing-Bong", "Hi-Lo", "Whoop"
                                                                                                        ]], 
                                                                                                        [name:"Volume",type:"ENUM", description:"Optional volume track is to be played at", optional:true,
                                                                                                         constraints:[null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]]] 
        
                
        command "playTrackAndRestore", [[name:"Track",type:"ENUM", description:"Track to be played", constraints:["Emergency", "Alert", "Warn", "Warning", "Tip",
                                                                                                                  "Fire", "Arpeggio", "Chime-down", "Chime-up",                                                                                                             
                                                                                                                  "Warble", "Whistle", "Bing-Bong", "Hi-Lo", "Whoop"
                                                                                                        ]], 
                                                                                                        [name:"Volume",type:"ENUM", description:"Optional volume track is to be played at", optional:true,
                                                                                                         constraints:[null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]]] 


        command "debug", ['boolean']
        command "connect"                       // Attempt to establish MQTT connection
        command "reset" 
        command "Repeat", [[name:"repeat",type:"ENUM", description:"Number of times to repeat audio", constraints:[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]]]
        command "EnableBeep"
        command "DisableBeep"
        command "EnableVoiceResults"
        command "DisableVoiceResults"
        
      //command "setWiFi"                       // As of 03-06-2022, was not supported by API  
        
        attribute "API", "String" 
        attribute "online", "String"
        attribute "firmware", "String"  
        attribute "signal", "String" 
        attribute "lastResponse", "String"
        attribute "lastTrack", "String"
        attribute "lastText", "String"
                
        attribute "mute", "String"    //ENUM ["unmuted", "muted"]
        attribute "volume", "Integer"     
        
        attribute "wifi_ssid", "String"  
        attribute "wifi_enabled", "String"  
        attribute "wifi_ip", "String"  
        attribute "wifi_gateway", "String"  
        attribute "wifi_mask", "String"
        attribute "ethernet_enabled", "String"       
        attribute "repeat", "Integer"    
        attribute "confirmationBeep", "Boolean"                            
        
        attribute "tracks", "String"    
        attribute "voiceResults", "Boolean"   
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
    
	log.trace "ServiceSetup(Hubitat dni=${state.my_dni}, Home ID=${state.homeID}, Name=${state.name}, Type=${state.type}, Token=${state.token}, Device Id=${state.devId})"
    
    reset(true)   
 }

def installed() {
 }

def updated() {
 }

def uninstalled() {
   interfaces.mqtt.disconnect() // Guarantee we're disconnected
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"  
   Announce("The speaker Hub is being uninstalled from Hubitat","Arpeggio","Arpeggio")      
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
        
        logDebug("pollAPI($request, $state.name, $state.type)") 
        
        def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("getDevicestate()> pollAPI() response: ${object}")                          
         
            if (successful(object)) {
                if (state.online != "true") {
                   state.online = "true"
                   sendEvent(name:"online", value: state.online, isStateChange:true)   
                }
                
                def firmware = object.data.version
                def wifi_ap = object.data.wifi.ap
                def wifi_ssid = object.data.wifi.ssid                    
                def wifi_enabled = object.data.wifi.enable                             
                def wifi_ip = object.data.wifi.ip  
                def wifi_gateway = object.data.wifi.gateway 
                def wifi_mask = object.data.wifi.mask       
                def ethernet_enabled = object.data.eth.enable
                
                def mute = object.data.options.mute                
                if (mute == false) {
                    mute = "unmuted"
                } else {
                    mute = "muted"
                }    
                
                def volume = object.data.options.volume
                def confirmationBeep = object.data.options.enableBeep                           
                           
                logDebug("Speaker Hub Firmware (${firmware}), " +
                         "WiFi ap(${wifi_ap}), " +
                         "WiFi SSID(${wifi_ssid}), " + 
                         "WiFi Enabled(${wifi_enabled}), " +
                         "WiFi IP(${wifi_ip}), " +
                         "WiFi Gateway(${wifi_gateway}), " +
                         "WiFi Mask(${wifi_mask}), " +
                         "Ethernet Enabled(${ethernet_enabled}), " +   
                         "Mute(${mute}), " +
                         "Volume(${volume}), " +       
                         "Confirmation Beep(${confirmationBeep})")
                        
                rememberState("firmware", firmware)
                rememberState("wifi_ap", wifi_ap)
                rememberState("wifi_ssid", wifi_ssid)
                rememberState("wifi_enabled", wifi_enabledx)
                rememberState("wifi_ip", wifi_ip)
                rememberState("wifi_gateway", wifi_gateway)
                rememberState("wifi_mask", wifi_mask)
                rememberState("ethernet_enabled", ethernet_enabled)
                rememberState("mute", mute)
                rememberState("volume", volume)
                rememberState("confirmationBeep", confirmationBeep)              
                                 
  	    	    rc = true	
                rememberState("online", "true") 
                lastResponse("Success")  	                
            } else {                
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

def check_MQTT_Connection() {
  def MQTT = interfaces.mqtt.isConnected()  
  logDebug("MQTT connected is ${MQTT}")  
  if (MQTT) {  
      rememberState("API","connected")
  } else {    
      rememberState("API","disconnected")
      establish_MQTT_connection(state.my_dni)      //Re-establish MQTT connection to YoLink API
  }
}    

def establish_MQTT_connection(mqtt_ID) {
      def authToken = parent.AuthToken()    
           
      def MQTT = "disconnected"
    
      def topic = "yl-home/${state.homeID}/${state.devId}/report"
    
      try {  	
         mqtt_ID =  "${mqtt_ID}_${state.homeID}"
         log.info "Connecting to MQTT with ID '${mqtt_ID}', Topic:'${topic}, Token:'${authToken}"    
      
         interfaces.mqtt.connect("tcp://api.yosmart.com:8003","${mqtt_ID}",authToken,null)                         	
          
         log.info "Subscribing to MQTT topic '${topic}'"    
         interfaces.mqtt.subscribe("${topic}", 0) 
         
         MQTT = "connected" 
         log.debug "MQTT connection to YoLink cloud successful"  
          
         voiceResult("Speaker Hub successfully connected to the YoLink Cloud")
		
	    } catch (e) {	
            log.error ("establish_MQTT_connection() Exception: $e")
            playText("Unable to connect Speaker Hub to YoLink Cloud",5)
            playText("Exception is: $e",5)
    	}
    
    rememberState("API",MQTT)
    lastResponse("API MQTT ${MQTT}")  
}    

def mqttClientStatus(String message) {                          
    log.debug "mqttClientStatus(${message})"

    if (message.startsWith("Error:")) {
        log.error "MQTT Error: ${message}"

        try {
            log.info "Disconnecting from MQTT"    
            interfaces.mqtt.disconnect()          // Guarantee we're disconnected
            rememberState("API","disconnected")
        }
        catch (e) {
        } 
    }
}

def parse(message) {
    def topic = interfaces.mqtt.parseMessage(message)
    def payload = new JsonSlurper().parseText(topic.payload)
    logger("trace", "parse(${payload})")

    processStateData(topic.payload)
}

def void processStateData(payload) {
    rememberState("online","true") 
    
    def object = new JsonSlurper().parseText(payload)
    def devId = object.deviceId   
   
    if (state.devId == devId) {  // Only handle if message is for me   
        logger("debug", "processStateData(${payload})")
        
        def child = parent.getChildDevice(state.my_dni)
        def name = child.getLabel()                
        def event = object.event.replace("${state.type}.","")
        log.debug "Received Message Type: ${event} for: $name"
        
        switch(event) {
		case "Alert":          
            def swState = object.data.state
            def alertType = object.data.alertType    
            def battery = parent.batterylevel(object.data.battery)    // Value = 0-4    
            def firmware = object.data.version    
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
            break;
                
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    } else { log.error "Event for other device received"}
}


def playTextAndRestore(text,volume=null) {playText(text,volume)}
def playTextAndResume(text,volume=null) {playText(text,volume)}
def playText(text,volume=null) {
    logDebug("playText($text,$volume)")
    if (text) {   
      playAudio(null,text,volume)        
      }
    }  

def playTrackAndRestore(track,volume=null) {playTrack(track,volume)}
def playTrackAndResume(track,volume=null) {playTrack(track,volume)}
def playTrack(track,volume=null) {
    if (track) {       
      playAudio(track,null,volume)
      }
    }    

def mute() {
    voiceResult("Muting speaker")
    rememberState("mute", "muted")    
    setOption(null,null,true)
    }

def unmute() {    
    rememberState("mute", "unmuted")
    setOption(null,null,false)
    voiceResult("Speaker is now unmuted")
    }

def EnableBeep() {
    rememberState("confirmationBeep", true)      
    setOption(null,true,null)
    voiceResult("Confirmation beep is now enabled")
    }

def DisableBeep() {
    rememberState("confirmationBeep", false)    
    setOption(null,false,null)
    voiceResult("Confirmation beep is now disabled")
    }

def EnableVoiceResults() {
    rememberState("voiceResults", true)          
    voiceResult("Voice results are now enabled")
    }

def DisableVoiceResults() {
    voiceResult("Voice results are now disabled")
    rememberState("voiceResults", false)       
    }

def setVolume(value) {    
    if (value < 0)  {
        value = 1
    } else {
        if (value > 16) {
            value = 16
        }
    }    
        
    rememberState("volume", value)   
    setOption(value,null,null)
    voiceResult("Speaker volume set to ${value}")
    }

def volumeDown() {
    def newvol = state.volume - 1
    setVolume(newvol)    
    }

def volumeUp() {
    def newvol = state.volume + 1
    setVolume(newvol)    
    }

def Repeat(repeat) {    
    rememberState("repeat", repeat)  
    voiceResult("Repeat set to ${repeat}")
    }

def voiceResult(text) {   
    if (state.voiceResults) {
      def repeat = state.repeat
      state.repeat=0
      playAudio(null,text)
      state.repeat=repeat    
      }
    }

def Announce(text, pretone="Chime-down", posttone="Chime-up", repeats=1, volume=5) {   
    def confirmationBeepCur = state.confirmationBeep
    def muteCur = state.mute
    def volumeCur = state.volume
    def repeatCur = state.repeat    
    def voiceResultsCur = state.voiceResults
    
    state.voiceResults=false
    
    DisableBeep()
    unmute()
    setVolume(volume)
                
    if (pretone) {
        state.repeat=repeats
        playTrack(pretone)
    }
    
    state.repeat=0        
    playAudio(null,text)
    
    if (posttone) {
        state.repeat=repeats
        playTrack(posttone)
    }
    
    if (confirmationBeepCur) {
        EnableBeep()
    }
    else{
        DisableBeep()
    }
    
    state.repeat=repeatCur  
    setVolume(volumeCur)                  //Reset volume
    
    if (muteCur=="muted") {
        mute()
    }
    else{
        unmute()
    }
    
    state.voiceResults = voiceResultsCur    
}

def playAudio(tone = null, message = null, volume = null) {  
   def mutestate = "" 
   if (state.mute == "muted") {
       log.warn "Attempting to play audio while device is muted"
       mutestate = " (device was muted!)" 
   } 
    
   //if (volume == null) {volume = state.volume}                          
       
   def params = [:] 
   
   if (tone != null) {params.put("tone", validTone(tone))
                      rememberState("lastTrack", tone + mutestate)
                     }
    
   if (message != null) {params.put("message", message)
                         rememberState("lastText", message + mutestate)
                        }
    
   if (volume != null)   {params.put("volume", volume.toInteger())}
   if (state.repeat != 0) params.put("repeat", Integer.valueOf(state.repeat))       // Integer(0~10),Optional:	Repeat times, 0 or null means not repeat. 
    
   log.trace "playAudio: ${params}" 
    
   def request = [:] 
   request.put("method", "SpeakerHub.playAudio")                
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)         
        if (object) {
            logDebug("playAudio(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {               
                logDebug("playAudio() was successful")
                /*
                if (message != null) {
                    def pause = message.length() * 30
                    pauseExecution(pause)                     //Give hub time to respond
                } else {
                    pauseExecution(250)                     //Give hub time to respond
                } */                   
            } else {
               pollError(object)  
            }    
	    } else { 			               
            log.error "playAudio() failed"	
        }     		
	} catch (e) {	
        log.error "playAudio() exception: $e"
	} 
}    

def setOption(volume=null, enableBeep=null, mute=null) {     
   def params = [:]   
   if (volume != null) {params.put("volume", Integer.valueOf(volume))}         // Integer,Optional: Global volume of device  
   if (enableBeep != null) {params.put("enableBeep", enableBeep)}              // Boolean,Optional:	Is beep enabled, True means the device will make a beep when performing some actions, such as startup, modify settings
   if (mute != null) {params.put("mute", mute)}                                // Boolean,Optional:	Is mute mode enabled, True means device will not make any sound,Even if you receive a message 
    
   def request = [:] 
   request.put("method", "SpeakerHub.setOption")                
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
        def swState
         
        if (object) {
            logDebug("setOption(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {                               
                logDebug("setOption() was successful")               
                
            } else {
                pollError(object)  
            }                         						
                
	    } else { 			               
            logDebug("setOption() failed")	            
        }     		
	} catch (e) {	
        log.error "setOption() exception: $e" 
	} 
}

/* As of 03-06-2022, was not supported by API         
void setWiFi() { 
   log.debug "setting WiFi: SSID=${settings.SSID}, Password=${settings.WiFiPassword}"

   def params = [:] 
   params.put("ssid", settings.SSID)    
   params.put("password", settings.WiFiPassword) 
    
   def request = [:] 
   request.put("method", "SpeakerHub.setWiFi")                
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
         
        if (object) {
            logDebug("setWiFi(): pollAPI() response: ${object}")                                                   
	    } else { 			               
            if (successful(object)) {                               
               logDebug("setWiFi() was successful")
           } else {
               pollError(object)  
           }      	            
        }     		
	} catch (e) {	
        log.error "setWiFi() exception: $e"
	} 
}   
*/

def validTone(tone) {
    def tonename = tone.toUpperCase().replace(".MP3","")
    def tones = hubTones().plus(",").toUpperCase()
    if (tones.contains(tonename.plus(","))) {         // Valid tone, correct the formatting
        switch(tonename) {
		case "EMERGENCY":          
            tonename = "Emergency"
			break;		
            
        case "ALERT":          
            tonename = "Alert"
			break;		
            
        case "WARN": case "WARNING":         
            tonename = "Warn"
			break;
            
        case "TIP":          
            tonename = "Tip"
			break;	
            
        case "FIRE": case "TEMPORAL-3-FIRE":          
            tonename = "TEMPORAL-3-FIRE"
			//fall through
                
		default:
            tonename = tonename + ".mp3"
			break;
	    }         
        
       return tonename
    } else {
       log.error "Tone '${tone}' is not a defined tone. Valid tones: '${tones}'" 
       return null 
    }
}

def hubTones() {
    return "Emergency, Alert, Warn, Warning, Tip, Fire, Arpeggio, Chime-down, Chime-up, Warble, Whistle, Bing-Bong, Hi-Lo, Whoop"    
}

def reset(setup = false){ 
    state.debug = false
    state.remove("API")
    state.remove("firmware")
    state.remove("wifi_enabled")   
    state.remove("wifi_ssid")
    state.remove("wifi_ip")
    state.remove("wifi_gateway")
    state.remove("wifi_mask")
    state.remove("ethernet_enabled")   
    state.remove("online")  
    state.remove("mute")  
    
    rememberState("tones", hubTones())
        
    state.voiceResults=false
    setOption(null,false,null) //Disable beep    
    setVolume(5)    
    setOption(null,null,false) //Unmute
    state.repeat=0 
        
    if (setup == false) {
        state.voiceResults=true
        Announce("Speaker hub reset requested, resetting device","Chime-down",null)    
    } else {
        state.voiceResults=false
    }

    unmute()
    setVolume(5)    
    Repeat(0)
    DisableBeep()
    
    interfaces.mqtt.disconnect()      // Guarantee we're disconnected  
    connect()                         // Reconnect to API Cloud
    
    DisableVoiceResults()
       
    poll(true)    

    if (setup == false) {
        Announce("Speaker hub reset complete.",null)      
    } else {
        Announce("Your speaker is now connected to your Hubitat Hub. Enjoy your Yo Link Speaker Hub!","Arpeggio","Arpeggio")        
    }
     
    state.remove("lastTrack") 
    state.remove("lastText") 
    
    logDebug("Device reset to default values")
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
