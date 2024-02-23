/***
 *  YoLink™ SpeakerHub (YS1604-UC)
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
 *  1.0.1: Fixed errors in poll()
 *  1.0.2: Send all Events values as a String per https://docs.hubitat.com/index.php?title=Event_Object#value
 *  1.0.3: def temperatureScale()
 *  1.0.4: Fix donation URL
 *  1.0.5: Support "Rules Engine" notification action
 *  1.0.6: Stop error message announcement "Unable to connect Speaker Hub to YoLink Cloud. Exception is: MqttException (0) - java.net.SocketTimeoutException: connect timed out"
 *  1.0.7: Remove MQTT Connection - device has no callbacks defined
 *  2.0.0: Sync version number with reengineered app due to new YoLink service restrictions
 *  2.0.1: Support diagnostics, correct various errors, make singleThreaded
 *  2.0.2: Copyright update and UI formatting
 *  2.0.3: Fix Rules compatability: 
 *         - Add capability "MusicPlayer"
 *  2.0.4: Add warnings for unsupported commands instead of causing an error.
 *  2.0.5: Prevent Service app from waiting on device polling completion
 *  2.0.6: Updated driver version on poll
 *  2.0.7: Support "setDeviceToken()"
 *         - Update copyright
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.7"}
def copyright() {return "<br>© 2022-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
def bold(text) {return "<strong>$text</strong>"}

preferences {
    input title: bold("Driver Version"), description: "YoLink™ SpeakerHub (YS1604-UC) v${clientVersion()}${copyright()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: bold("Please donate"), description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
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
    definition (name: "YoLink SpeakerHub Device", namespace: "srbarcus", author: "Steven Barcus", singleThreaded: true) {     		
		capability "Polling"	
        capability "AudioNotification"
        capability "AudioVolume"
        capability "Notification"
        capability "MusicPlayer"
        
        
        command "restoreTrack"        // Override command definition - not relevant
        command "resumeTrack"         // Override command definition - not relevant
        command "setTrack"            // Override command definition - not relevant
        command "playTrackAndResume"  // Override command definition - not relevant
        command "playTrackAndRestore" // Override command definition - not relevant 
        command "playTextAndResume"   // Override command definition - not relevant
        command "playTextAndRestore"  // Override command definition - not relevant
        
        command "setVolume", [[name:"volume",type:"ENUM", description:"Speaker volume", constraints:[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]]]
        command "setLevel", [[name:"volume",type:"ENUM", description:"Speaker volume", constraints:[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]]] 
        
        command "playText", [[name:"Text",type:"STRING", description:"Text to be played"], 
                            [name:"Volume",type:"ENUM", description:"Optional volume text is to be played at", optional:true,
                             constraints:[null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]]] 
        
        command "playTrack", [[name:"Track",type:"ENUM", description:"Track to be played", constraints:["Emergency", "Alert", "Warn", "Warning", "Tip",
                                                                                                        "Fire", "Arpeggio", "Chime-down", "Chime-up",                                                                                                             
                                                                                                        "Warble", "Whistle", "Bing-Bong", "Hi-Lo", "Whoop"
                                                                                                        ]], 
                                                                                                        [name:"Volume",type:"ENUM", description:"Optional volume track is to be played at", optional:true,
                                                                                                         constraints:[null, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]]] 
        
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]]
        command "reset" 
        command "Repeat", [[name:"repeat",type:"ENUM", description:"Number of times to repeat audio", constraints:[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]]]
        command "EnableBeep"
        command "DisableBeep"
        command "EnableVoiceResults"
        command "DisableVoiceResults"
        command "deviceNotification", [[name:"deviceNotification",type:"STRING", description:"Text to be played on SpeakerHub"]]
        
      //command "setWiFi"                       // As of 03-06-2022, was not supported by API  
        
        attribute "online", "String"
        attribute "devId", "String"
        attribute "driver", "String"  
        attribute "firmware", "String"
        attribute "lastPoll", "String"
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
    
	log.trace "ServiceSetup(Hubitat dni=${state.my_dni}, Home ID=${state.homeID}, Name=${state.name}, Type=${state.type}, Token=${state.token}, Device Id=${state.devId})"
    
    reset(true)   
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
   Announce("The speaker Hub is being uninstalled from Hubitat","Arpeggio","Arpeggio")      
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

def temperatureScale(value) {}

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
             logDebug("getDevicestate(): pollAPI() response: ${object}")                           
         
            if (successful(object)) {
                def firmware = object.data.version.toUpperCase()
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
                rememberState("wifi_enabled", wifi_enabled)
                rememberState("wifi_ip", wifi_ip)
                rememberState("wifi_gateway", wifi_gateway)
                rememberState("wifi_mask", wifi_mask)
                rememberState("ethernet_enabled", ethernet_enabled)
                rememberState("mute", mute)
                rememberState("volume", volume)
                rememberState("confirmationBeep", confirmationBeep)              
                                
                rememberState("online", "true") 
                rc = true
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

def playTrackAndResume() {unsupported("Play Track And Resume")}
def playTrackAndRestore() {unsupported("Play Track And Restore")} 
def playTextAndResume() {unsupported("Play Text And Resume")}  
def playTextAndRestore() {unsupported("Play Text And Restore")}
def nextTrack() {unsupported("Next Track")}
def pause() {unsupported("Pause")}
def play() {unsupported("Play")}
def previousTrack() {unsupported("Previous Track")}
def restoreTrack() {unsupported("Restore Track")}
def resumeTrack() {unsupported("Resume Track")}
def setTrack() {unsupported("Set Track")}
def stop() {unsupported("Stop")}

def unsupported(cmd) {log.warn "The '$cmd' command is not supported on a SpeakerHub"}

def deviceNotification(text) {playText(text,null)}

def playText(text,volume=null) {
    logDebug("playText($text,$volume)")
    if (text) {   
      playAudio(null,text,volume)        
      }
    }  

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

def setLevel(value) {   
    setVolume(value)
    }

def setVolume(value) {   
    value = value.toInteger() 
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
    def newvol = state.volume.toInteger() - 1
    setVolume(newvol)    
    }

def volumeUp() {
    def newvol = state.volume.toInteger() + 1
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
    state.remove("driver")
    rememberState("driver", clientVersion()) 
    state.remove("online")
    state.remove("firmware")
    state.remove("wifi_enabled")   
    state.remove("wifi_ssid")
    state.remove("wifi_ip")
    state.remove("wifi_gateway")
    state.remove("wifi_mask")
    state.remove("ethernet_enabled")   
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
    DisableVoiceResults()
       
    poll(true)    

    if (setup == false) {
        Announce("Speaker hub reset complete.",null)      
    } else {
        Announce("Your speaker is now connected to your Hubitat Hub. Enjoy your Yo Link Speaker Hub!","Arpeggio","Arpeggio")        
    }
     
    state.remove("lastTrack") 
    state.remove("lastText") 
    
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
  if (state.debug == "true") {log.debug msg}
}