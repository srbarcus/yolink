/**
 *  YoLink™ Device Service
 *  © (See copyright()) Steven Barcus. All rights reserved.
 *  THIS SOFTWARE IS NEITHER DEVELOPED, ENDORSED, OR ASSOCIATED WITH YoLink™ OR YoSmart, Inc.
 *
 *  Donations are greatly appreciated: https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1&currency_code=USD 
 *   
 *  Developer retains all rights, title, copyright, and interest, including patent rights and trade
 *  secrets in this software. Developer grants a non-exclusive perpetual license (License) to User to use
 *  this software under this Agreement. However, the User shall make no commercial use of the software without
 *  the Developer's written consent. Software Distribution is restricted and shall be done only with Developer's written approval.
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied. 
 * 
 *  1.0.1: Add support to determine correct driver for Temperature only sensors. Removed some superfluous log messages.
 *  1.0.2: Return converted temperature as a String value.
 *  1.0.3: Reduce number of messages by adding debug setting, misc. fixes
 *  1.0.4: Fix "No such property: statusCode for class: java.net.SocketTimeoutException" error, improved polling error diagnostic messages
 *  1.0.5: Return temperatures as a Number rounded to 1 decimal place, return battery level as a Number.
 *  1.0.6: Remove any possible leading and/or trailing spaces in credentials.
 *  1.0.7: - Add temperature scale function
 *         - Sync temperature scale changes with all devices - NOTE: YoLink mobile app settings will override!
 *  1.0.8: Fix donation URL
 *  2.0.0: - Reengineer app and drivers to use centralized MQTT listener due to new YoLink service restrictions
 *         - Update API return code translations  
 *  
 *  2.1.00: Add support for Smart Outdoor Plug (YS6802-UC/SH-18A)
 *  2.1.01: Speed up execution
 *  2.1.02: Allow device driver to override temperation conversion scale
 *  2.1.03: Initialize MQTT listener whenever app is run
 *          - Correct problem with scheduling under 5 minutes 
 *  2.1.04: Allow collection of diagnostic information
 *          - Correct problem with devices not being polled after hub reboot
 *  2.1.05: Correct numerous 'Request was unauthorized. Attempting to refreshing access token and re-poll API.' messages
 *          - Add 500ms delay between polling of next device to reduce load on Hubs
 *  2.1.06: Add support for Leak Sensor 3 (YS7904-UC)
 *  2.1.07: Add link to Hubitat Community, update copyright date, clean up UI, performance improvements
 *  2.1.08: Correct problem with temperature scale being overridden on defined devices 
 *          - Support syncing of application name changes
 *          - Add description to settings
 *          - Improve diagnostics collection performance
 *  2.1.09: Return null battery level value as "0" 
 *  2.1.10: Copyright update
 *  2.1.11: Handle new error code: 010104:Header Error!The token expired
 *  2.1.12: Reduce instantaneous hub load when polling devices
 *          -Return name of device that MQTT message was passed to back to MQTT Listener for debugging
 *  2.1.13: Add retry to pollAPI() if connection timesout
 *  2.1.14: Added findChild()
 *  2.1.15: Fixed polling error caused by API returning new error description: "Invalid request: The token is expired"
 *  2.1.16: Copyright date update
 *          - Handle device toke error
 *          - Efficency improvements
 *  2.1.17: Display installation errors
 *  2.1.18: Retry busy device requests
 */
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.net.URLEncoder
import groovy.transform.Field

private def get_APP_VERSION() {return "2.1.18"}
private def copyright() {return "<br>© 2022-" + new Date().format("yyyy") + " Steven Barcus. All rights reserved."}
private def get_APP_NAME() {return "YoLink™ Device Service"}

definition(
    name: "YoLink™ Device Service",
    namespace: "srbarcus",
    author: "Steven Barcus",
    description: "Connects your YoLink™ devices to Hubitat.",
    oauth: false,    
    category: "YoLink",
    singleInstance: true,
    iconUrl: "${getImagePath()}yolink.png",
    iconX2Url: "${getImagePath()}yolink.png",
    importUrl: "https://github.com/srbarcus/yolink/edit/main/YoLink_Device_Service.groovy"
)

preferences {
	page(name: "about", title: "About", nextPage: "credentials") 
    page(name: "credentials", title: "YoLink™ App User Access Credentials", content:"credentials", nextPage:"otherSettings")
    page(name: "otherSettings", title: "Other Settings", content:"otherSettings", nextPage: "deviceList")
    page(name: "deviceList",title: "YoLink™ Devices", content:"deviceList",nextPage: "diagnostics")  	
    page(name: "diagnostics", title: "YoLink™  Device Service and Driver Diagnostics", content:"diagnostics", nextPage: "finish")
    page(name: "finish", title: "Installation Complete", content:"finish", uninstall: false)
}

@Field static diagFile = "YoLink_Service_Diagnostics.txt"
@Field static errFile = "YoLink_Service_Errors.txt"
@Field static String diagsep = "-----------------------------------------------------------------------------------------------------------------------------------------------"
String diagData
String errData 

def about() {
 	dynamicPage(name: "about", title: pageTitle("About"), uninstall: true) {
 		section("") {	
			paragraph image:"${getImagePath()}yolink.png", boldTitle("${get_APP_NAME()} - Version ${get_APP_VERSION()}")
			paragraph boldTitle("This app connects your YoLink™ devices to Hubitat via the cloud.")  
            paragraph blueTitle(
            "The app is neither developed, endorsed, or associated with YoLink™ or YoSmart, Inc." + 
	        "</br>Provided 'AS IS', without warranties or conditions of any kind, either expressed or implied.") 		
            paragraph boldTitle ("")
            paragraph "Refer to the <a href=https://community.hubitat.com/t/release-beta-yolink-device-service-app-and-drivers-to-connect-hubitat-to-yolink-devices/96432>Hubitat Community Discussion</a> for the latest information and installation help."
            paragraph boldTitle ("")
			paragraph "Please donate and support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development:"
                href url:"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1&currency_code=USD", title:"Make a Paypal donation..."
			paragraph boldTitle (copyright())	 
            paragraph boldTitle ("")
            paragraph boldTitle ("")
            if (removeDevices != "False"){
              paragraph boldRedTitle ("WARNING: Removing this application will delete all Hubitat devices created by the app! (This option can be disabled in the additional settings before removal)")
            }    
		}
        section("") {          
			input "debugging", "enum", title:boldTitle("Enable Debugging"), required: true, options:["True","False"],defaultValue: "False"  
		}  
	}        
}

def credentials() {    
  	dynamicPage(name: "credentials", title: pageTitle("YoLink™ Access Credentials"), uninstall: false) {
        section(""){
			paragraph "These values must match the YoLink™ mobile app values precisely. Take care to insure upper and lowercase letters, as well as similar looking characters such as '0' (zero) and 'O' (oh) and '1' (one) and 'l' (ell), are specified exactly as shown in the app."
		    }    
		section(sectionTitle("User Access Credentials from the YoLink™ mobile app")) {
		    input "UAID", "text", title: boldTitle("UAID:"), required: true	    	
            input "secret_key", "text", title: boldTitle("Secret Key:"), required: true	       
	    }
        section("") {	
			paragraph "Click here for information on obtaining your User Access Credentials from the YoLink™ mobile app" 
				href url:"https://github.com/srbarcus/yolink/blob/main/Help/Obtaining_your_Personal_Access_Credentials.pdf",
	 				title:"How to obtaining your User Access Credentials (UAC)"			
		}
     } 
}

def otherSettings() {    
    if (!pollInterval){pollInterval = 5}
    if (!syncNames){syncNames = True}
    if (!removeDevices){removeDevices = True}
    dynamicPage(name: "otherSettings", title: pageTitle("Other Settings"), uninstall: false) {
        section() {
			input "temperatureScale", "enum", title:boldTitle("Temperature Scale (Celsius or Fahrenheit)") + "</br>Scale used to report temperatures on newly defined devices. Can be overridden individually on each device definition.", required: true, options:["C","F"],defaultValue: "F"
		    input "pollInterval", "enum", title:boldTitle("Device polling interval in minutes") + "</br>Interval at which devices are polled. Mostly used to determine if device is still online, but some devices may need to be polled to update their settings. 5 Minutes is the recommended interval.", required: true, options:[1,2,3,4,5,10,15,30], defaultValue: 5
            input "syncNames", "enum", title:boldTitle("Synchronize device names with YoLink Mobile app") + "</br>Forces devices to be renamed to match their name in the YoLink Mobile application. To synchronize the names, simply rerun this app from start to finish after renaming the device(s) in the YoLink mobile app.", required: true, options:["True","False"], defaultValue: "True"
			input "removeDevices", "enum", title:boldTitle("Remove associated Hubitat devices when this app is removed") + "</br>This is for advanced users only. Setting it to False will result in orphaned device definitions if the app is removed.", required: true, options:["True","False"], defaultValue: "True"
       	}
	}
}

def deviceList() {
    if (!refreshAuthToken()){
        dynamicPage(name: "deviceList", title: pageTitle("Error obtaining access token"), nextPage:"credentials", uninstall: false) {
        section(boldTitle("The YoLink server returned the following error:")){
            paragraph "${state.token_error}"				
			}    
		section(""){
			paragraph "Click Next to respecify your User Access Credentials from the YoLink modile app."
		    }
	    }        
    } else {
 	    def devices=getDevices()
        int devicesCount=devices.size()    
        
        log.info "$devicesCount YoLink devices were found"	    
	    dynamicPage(name: "deviceList", title: sectionTitle("$devicesCount YoLink devices were found. Select the devices you want available to Hubitat"), uninstall: false) {
    		section(""){
	    		paragraph "Click below to see the list of devices available in your YoLink home"
		    	input(name: "exposed", title:"", type: "enum",  description: "Click to choose", options:devices, multiple:true)
                paragraph boldTitle ("")                
                paragraph boldTitle ("")
                paragraph boldTitle ("Note: Clicking 'Next' will create the selected devices and/or delete the deselected devices. This will take awhile if you have many devices, please be patient.") 
		    }
	    }
    }
}

def diagnostics() {   
    body = JsonOutput.toJson(name:errFile,type:"file")
    params = [
              uri: "http://127.0.0.1:8080",
              path: "/hub/fileManager/delete",
              contentType:"text/plain",
              requestContentType:"application/json",
              body: body
              ]
    httpPost(params) {resp}
    errData = ""
    state.errors=""
    
    getGeneralInfo()	
    
    def Keep_Hubitat_dni = ""
    int countNewChildDevices = 0   
    int devicesCount= 0
    
    if (exposed) {devicesCount=exposed.size()}
        
    log.info "$devicesCount devices were selected"
    
    if (devicesCount > 0 ) {
    exposed.each { dni ->   
       def devname = state.deviceName."${dni}"
       def devtype = state.deviceType."${dni}"
       def devtoken = state.deviceToken."${dni}"
       def devId = state.deviceId."${dni}"
       def devmodel = state.modelName."${dni}"
       log.info "Creating selected Device: ${devname} - ${devtype} ${devmodel} "	
       def Hubitat_dni = "yolink_${devtype}_${dni}"
       Hubitat_dni = create_yolink_device(Hubitat_dni, devname, devtype, devtoken, devId)
       if (Hubitat_dni != null) {
          Keep_Hubitat_dni = Keep_Hubitat_dni.plus(Hubitat_dni)
          countNewChildDevices++     
          logDebug("Created $countNewChildDevices of ${exposed.size()} selected devices.")    
       }
    } 	 
    
    def devname = "YoLink Cloud Listener"
    def devtype = "MQTT Listener"
    def devtoken = state.UAID.plus("MQTT1")
    def devId = state.UAID.plus("MQTT1")    
    def Hubitat_dni = "yolink_${devtype}_${devId}"
    Hubitat_dni = create_yolink_device(Hubitat_dni, devname, devtype, devtoken, devId)
    if (Hubitat_dni != null) {Keep_Hubitat_dni = Keep_Hubitat_dni.plus(Hubitat_dni)}  
    }    
       
    def children= getChildDevices()
	children.each { 
        if (!Keep_Hubitat_dni.contains(it.deviceNetworkId)){
          log.warn "Device ${it} (${it.deviceNetworkId}) is no longer selected. Deleting the device."
          deleteChildDevice(it.deviceNetworkId)   
        }    
 	}
    
    if ($errData != null) {
      errData = appendData(errData, diagsep)  
      log.error "Installation errors written to 'YoLink_Service_Errors.txt'."  
      writeFile(errFile, errData)
    } 
    
    state.errors = errData
    
	dynamicPage(name: "diagnostics", title: pageTitle("YoLink™ Device Service Diagnostics"), uninstall: false) {       	
        section(""){			
            paragraph boldRedTitle ("Only set these options to True if instructed to by the Developer. No personal information is collected or sent. The diagnostics data is written to a file on your Hubitat so you can see what's collected.") 
            paragraph boldTitle ("")                
            paragraph boldTitle ("")
   		    input "reset", "enum", title:boldTitle("Perform a reset on all YoLink™ devices"), required: true, options:["True","False"], defaultValue: "False" 
		    input "diagnose", "enum", title:boldTitle("Enable collection of diagnostic data to local file."), required: true, options:["True","False"], defaultValue: "False" 
            }
    }
}

def finish() {    
  if (reset == "True") {
        def children= getChildDevices()                
	    children.each { 
          it.reset()          
        }   
  }  
    
  body = JsonOutput.toJson(name:diagFile,type:"file")
  params = [
            uri: "http://127.0.0.1:8080",
            path: "/hub/fileManager/delete",
            contentType:"text/plain",
            requestContentType:"application/json",
            body: body
            ]
  httpPost(params) {resp}    
    
  if (diagnose == "True") {  
        diagData = ""
        
        def date = new Date(now() )    
        date = date.format("MM/dd/yyyy hh:mm:ss a" )
        
        diagData = appendData(diagData,"YoLink Device Service Diagnostics - Collected $date")
        diagData = appendData(diagData, "YoLink Device Service App Version ".plus(get_APP_VERSION())) 
     
        def hubitat = location.hub
        diagData = appendData(diagData, "Hubitat information: Hardware=$hubitat.hardwareID  Firmware=$hubitat.firmwareVersionString  Uptime=$hubitat.uptime")   
          
        def devices=getDevices() 
        int deviceCount=devices.size()  
        diagData = appendData(diagData, " ")    
        diagData = appendData(diagData, "YoLink Hub reported $deviceCount devices")
            
        def yodev = [] 
        def alldev = [] 
        def devmodel
      
        devices.each {
          def NameDNI = it.toString()
          def DNI = NameToDNI(NameDNI)
            
          devmodel = state.modelName[DNI]             
          
          def diag = "Device ${it} [${devmodel}}"  
            
          yodev.add(diag)
          alldev.add(diag)
        }   
      
        def sortedlst = yodev.sort() 
        sortedlst.each { 
          diagData = appendData(diagData, it)
        }   
        
        def children= getChildDevices()
        int childcount=children.size()  
 
        diagData = appendData(diagData, " ")
        diagData = appendData(diagData, diagsep)
        diagData = appendData(diagData, "YoLink Device Service has $childcount devices defined")
       
        def hubdev = []   
        children.each { 
             def diag = "Device ${it.currentValue("devId", true)}=${it} v${it.currentValue("driver", true)} (Setup:${it.isSetup()}, Online:${it.currentValue("online", true)}, Firmware:${it.currentValue("firmware", true)})"  
             hubdev.add(diag)
             alldev.add(diag)
        } 
      
        sortedlst = hubdev.sort() 
        sortedlst.each { 
          diagData = appendData(diagData, it)
        }   

        diagData = appendData(diagData, " ")
        diagData = appendData(diagData, diagsep)
        diagData = appendData(diagData, "YoLink and Hubitat Device Cross-Reference")
      
        sortedlst = alldev.sort() 
        sortedlst.each { 
          diagData = appendData(diagData, it)
        }   
     
        diagData = appendData(diagData, diagsep) 
      
        if (state.errors != "") {
          diagData = appendData(diagData, state.errors)  
        } 
      
        writeFile(diagFile,diagData)
  }    

  dynamicPage(name: "finish", title: pageTitle("Processing Complete"), install: true) {  
  if (state.errors != "") {
     section(boldRedTitle("The installation returned the following error(s):")){
       paragraph "${state.errors}"
       }  
     section("") {	    
       paragraph "View the error file at <a href=http://${location.hub.localIP}:8080/local/YoLink_Service_Errors.txt target='_blank' rel='noopener noreferrer'>YoLink_Service_Errors.txt</a>"
       }
  } 
    
  if (diagnose == "True") {
     section("") {	
       paragraph "Diagnostic data has been collected. Copy and Paste the text into a PM message to the developer." 
       paragraph "View the file at <a href=http://${location.hub.localIP}:8080/local/YoLink_Service_Diagnostics.txt target='_blank' rel='noopener noreferrer'>YoLink_Service_Diagnostics.txt</a>"
       }
  } else {
     section("") {	
       paragraph "Click 'Done' to exit." 
       }
  }       
}
}    

def installed() {
    log.info "${get_APP_NAME()} app installed."  
    subscribe(location, "systemStart", initialize)
    runIn(5, initialize)
}

def updated() {
	log.info "${get_APP_NAME()} app updated."  
    unsubscribe()
    subscribe(location, "systemStart", systemStart)    
    runIn(5, initialize)
}

void systemStart(evt){	
    log.info "${get_APP_NAME()} app starting up."  
	runIn(5, initialize)
}

def initialize() {    
    unschedule()    
    refresh()
}    

def refresh() {
   	pollDevices()   
    schedulePolling()
}        

def uninstalled() {    
    unschedule()
    log.warn "Uninstalling ${get_APP_NAME()} app"
    if (removeDevices != "False"){
      delete_child_devices(getChildDevices()) 
    }   
}

private create_yolink_device(Hubitat_dni,devname,devtype,devtoken,devId) { 	
    def drivername = devtype
        
    if (devtype == "THSensor") {
      log.info "$devname is a THSensor, determining device's capabilities..."  
      drivername = getTHSensorDriver(devname,devtype,devtoken,devId) 	
    }   
    
    if (devtype == "LeakSensor") {
      log.info "$devname is a LeakSensor, determining device's capabilities..."  
      drivername = getLeakSensorDriver(devname,devtype,devtoken,devId) 	
    }   
    
    if (devtype == "MultiOutlet") {
      log.info "$devname is a MultiOutlet, determining device's capabilities..."  
      drivername = getMultiOutletDriver(devname,devtype,devtoken,devId) 	
    }   
   
    def newdni = Hubitat_dni
    drivername = getYoLinkDriverName("$drivername")     
        
	def dev = allChildDevices.find {it.deviceNetworkId.contains(newdni)}	
    
    def failed = false
    
    def labelName = "YoLink $devtype - ${devname}"

	if (!dev) {
		logDebug("Creating child device named ${devname} with id $newdni, Type = $devtype,  Device Token = $devtoken, Driver = $drivername")
        
        try {
           dev = addChildDevice("srbarcus", drivername, newdni, null, [label:"${labelName}"])
                         
           if (devtype == "SpeakerHub") {
              log.trace "A speaker Hub named '$devname' has been located." 
              state.speakerhub = newdni
           }  
            
		} catch (Exception e) {
            failed = true
            if ($errData == null) {
              def date = new Date(now() )    
              date = date.format("MM/dd/yyyy hh:mm:ss a" )
        
              errData = appendData(errData,"YoLink Device Service Installation Errors - Created $date")
              errData = appendData(errData, "YoLink Device Service App Version ".plus(get_APP_VERSION()))
     
              def hubitat = location.hub
              errData = appendData(errData, "Hubitat information: Hardware=$hubitat.hardwareID  Firmware=$hubitat.firmwareVersionString  Uptime=$hubitat.uptime")   
                
              errData = appendData(errData, diagsep)
            }             
            
    		log.error "An error occcurred while trying add child device '$labelName'"
            errData = appendData(errData, "An error occcurred while trying to add child device '$labelName'")
                                
            if (e.message.contains("Please use a different DNI")) {    //Could happen if user uninstalled app without deleting previous children                 
                 log.error "A device with dni '$newdni' already exists. Delete the device and try running the " + get_APP_NAME() + " app again."
                 errData = appendData(errData, "A device with dni '$newdni' already exists. Delete the device and try running the " + get_APP_NAME() + " app again.")
            } else {
               if (e.message.contains("not found")) {                  
                  log.error "Unable to create device '$devname' because driver '$drivername' is not installed. Either the device is not currently supported by the YoLink Device Service app or you need to install the driver using the 'Modify' option in the 'Hubitat Package Manager' app."
                  errData = appendData(errData, "Unable to create device '$devname' because driver '$drivername' is not installed.")
                  errData = appendData(errData, "Either the device is not currently supported by the YoLink Device Service app or you need to install the driver using the 'Modify' option in the 'Hubitat Package Manager' app.") 
               } else {
                      log.error "Exception: '$e'"
                      errData = appendData(errData, "Exception: '$e'")
                      }  
            }
            errData = appendData(errData, " ")
        } finally {
            if (failed) {return null}
        }    
            
        logDebug("Calling child device setup: $newdni, $state.homeID, $devname, $devtype, $devtoken, $devId")
        dev.ServiceSetup(newdni,state.homeID,devname,devtype,devtoken,devId) 	// Initial setup of the Child Device   
        logDebug("Syncing temperature scale on device ${dev}")
        try {                                                //Not all devices support temperature
            dev.temperatureScale(settings.temperatureScale)  
	    } catch (Exception e) {                 
            logDebug("Temperature scale not supported on device ${dev}")                  
        }   
		logDebug("Setup of ${dev.displayName} with id $newdni has been completed")            
	   
	} else {
		log.info "Child device ${dev.displayName} already exists with id $newdni, new device not created"
        if (devtype == "SpeakerHub") {
           log.trace "A speaker Hub named '$devname' has been located." 
           state.speakerhub = newdni
        }  
        
        def currentName = dev.getLabel()
        
        if (labelName != currentName) {
            if (syncNames == "True") {
              log.warn "Device '${currentName}' was renamed in the YoLink mobile app to '${labelName}'. Renaming Hubitat device."
              dev.setLabel(labelName)
            } else {
              log.warn "Device '${currentName}' was renamed in the YoLink mobile app to '${labelName}', but name synchronizing is set to 'False'. Device was not renamed on Hubitat."
            }
        }
	}
    
    if ((dev) && (devtype == "MQTT Listener")) {
           log.info "Initializing MQTT Listener Device"
           dev.initialize()
    }    
    
    return newdni
}

def getDeviceToken(dni) {
    def body = [:]
    body.put("method", "Home.getDeviceList") 

    log.info "Polling API to retrieve device tokens..."   
    
	try {         
        def object = pollAPI(body,"YoLink™ Device Service","App")
         
        if (object) {
            def responseValues=[]              
            
            if (object.data.devices instanceof Collection) { 
       		    responseValues=object.data.devices           
                logDebug("Parsing multiple devices: ${responseValues}")
    	    } else {
			    responseValues[0]=object.data.devices                    
                logDebug("Parsing single device: ${responseValues}")
		    }                
           
            for (def device : responseValues) {               
              if (device.deviceId == dni) {	
                 logDebug("Located ${device.name}")
                 //if (device.token != state.deviceToken[dni]) {
                 //  log.warn "Device token '${ state.deviceToken[dni]}' changed to '${device.token}'"
                   def child = findChild(dni)
                   child.setDeviceToken(device.token)
                 //} else {
                 //    log.error "Device token '${ state.deviceToken[dni]}' is already '${device.token}'"
                 //}  
                 state.deviceToken[dni] = device.token 
                 break
              }
            }
		} else { 			               
  			log.error "getDeviceToken failed"	                
		} 
	} catch (groovyx.net.http.HttpResponseException e) {	
            log.error "Error executing getDeviceToken, Exception: $e"
			if (e?.statusCode == UNAUTHORIZED()) { 
				log.warn "Unauthorized Request. Insure your access credentials match those in your YoLink mobile app"
			}         
	}
} 

def schedulePolling() {		 
    def interval = (pollInterval) ? pollInterval.toString(): "5" // Default: Poll every 5 minutes 
    def seconds = interval.toInteger() * 60
        
    logDebug("Scheduling device polling for ${interval} minutes (${seconds} seconds)")
    
    runIn(seconds, pollDevices)     
}

def pollDevices() {
    schedulePolling()
    
    def delay = 0
    
    def children= getChildDevices()
    children.each { 
       try {     
           logDebug("Scheduling polling on device ${it} (${it.deviceNetworkId}), delay = ${delay} seconds")
           it.pollDevice(delay)                         
           
           delay = delay + 10
           
	   } catch(Exception e) {
           log.error "pollDevices() Exception: $e"
           log.error "Device: ${it}"
       }  
    }
    logDebug("Polling acheduled on all devices.")
}

def passMQTT(topic) {
    logDebug("passMQTT(${topic})")
        
    def payload = new JsonSlurper().parseText(topic.payload)
    logDebug("payload (${payload})")
    
    def deviceDNI = payload.deviceId
    
    logDebug("Searching for deviceID ${deviceDNI}")
    
    def dev = allChildDevices.find {it.deviceNetworkId.contains(deviceDNI)}
    
    if(dev) {
        logDebug("Passing MQTT message ${topic} to device ${dev} (${dev.deviceNetworkId})")
        dev.parse(topic)
        return "${dev}"
    } else {      
        log.error "Unable to locate target device ${deviceDNI} for MQTT message ${topic}. Make sure the YoLink device has been defined via the YoLink Device Service app."
        return null
    }    
}

private SpeakerHub() {    
    def dev = allChildDevices.find {it.deviceNetworkId.contains(state.speakerhub)}    
    if(dev) {
       logDebug("SpeakerHub() = $dev.label")
       return dev
    } else {    
       log.info "SpeakerHub(): No speaker hub available"  
       return null
    }    
}  

private def delete_child_devices(delete=null) {	
	if (delete) {
       	delete.each {
     		log.warn "Deleting ${it.displayName}"
           	deleteChildDevice(it.deviceNetworkId)        
		}            
        	return        
    	}           
}

def getYoLinkDriverName(devtype) {
    def driver = "YoLink ${devtype} Device" 
    log.info "Driver name for type ${devtype} is '${driver}'"
    return driver
    }

private def apiURL() { return 'https://api.yosmart.com/open/yolink/v2/api'} 
private def mqqtURL() { return 'http://api.yosmart.com'} 
private def tokenURL() {return "https://api.yosmart.com/open/yolink/token"}
private def getStandardImagePath() {return "http://cdn.device-icons.smartthings.com/"}
private int SUCCESS() {return 200}
private int UNAUTHORIZED() {return 401}
private def getImagePath() {return "https://raw.githubusercontent.com/srbarcus/yolink/main/icons/"}   

def AuthToken() {state.access_token}  
def refreshAuthToken() {   
	logDebug("Refreshing access token")
	boolean rc=false    
            
    state?.UAID = UAID.trim()
    state?.secret_key = secret_key.trim()
    
    state.remove("access_token")            // Remove any old token  
   	
    def query = [:]
    query.put("grant_type", "client_credentials")    
    query.put("client_id", "${state.UAID}")    
    query.put("client_secret", "${state.secret_key}")    
    
	def refreshParams = [
			uri   : tokenURL(),
            query : query
	]

    logDebug("Attempting to get access token using parameters: ${refreshParams}")
    
	try {     
		httpPostJson(refreshParams) { resp ->                     
            
			if (resp.status == SUCCESS()) {	
                logDebug("API Response: SUCCESS")
				def object = resp.data

				if (object) {                    
					logDebug("Token Server Response: ${resp.data}")
                    
                    def tokenState = object?.state
                    def tokenMsg = object?.msg                    
                    
                    if (tokenState == "error") {
                        state.token_error = tokenMsg 
                        log.error "Authorization token refresh failed: ${tokenMsg}"                     
                    } else {                                       
                        state.access_token = object?.access_token                    
                        state.access_token_ttl = object?.expires_in                    
                        logDebug("New access token = ${state.access_token}")
                        rc = true
                    }
				}              
			} else { 				
				log.error "Refreshing access token failed ${resp.status} : ${resp.status.code}"				
			} 
		} 
	} catch (groovyx.net.http.HttpResponseException e) {	
			if (e?.statusCode == UNAUTHORIZED()) { 
    	     log.warn "Unauthorized Request. Insure your access credentials match those in your YoLink mobile app"
            } else {
             log.error ("Error refreshing access token, Exception: $e")  
            }    
	}  
    
    logDebug("refreshAuthToken() RC = ${rc}")
    
	return rc
}

boolean getGeneralInfo() {	                      
    def body = [:]
        body.put("method", "Home.getGeneralInfo") 
                    
    logDebug("Polling API to retrieve general info...")
    
	try {         
        def object = pollAPI(body,"YoLink™ Device Service","App")
         
        if (object) {
                logDebug("pollAPI() response: ${object}")
                
                def code = object.code               
                def time = object.time
                def msgid = object.msgid  
                def desc = object.desc 
                state.homeID = object.data.id    //ID of home
            
                logDebug("Home ID: ${state.homeID}")
                    
                return true							
                
			} else { 			               
				log.error "Error getting GeneralInfo"	
                return false
			} 
    
		
	} catch (groovyx.net.http.HttpResponseException e) {	
            log.error "Error getting GeneralInfo, Exception: $e"
			if (e?.statusCode == UNAUTHORIZED()) { 
				log.warn "Unauthorized Request. Insure your access credentials match those in your YoLink mobile app"
			}            
	}
}    

def findChild(dni) {
    return allChildDevices.find {it.deviceNetworkId.contains(dni)}	        
}    

String pageTitle 	(String txt) 	{ return '<h2>'+txt+'</h2>'}
String sectionTitle	(String txt) 	{ return '<h3>'+txt+'</h3>'}
String blueTitle	(String txt)	{ return '<span style="color:#0000ff">'+txt+'</span>'} 
String smallTitle	(String txt)	{ return '<h3 style="color:#8c8c8c"><b>'+txt+'</b></h3>'} 
String boldTitle	(String txt) 	{ return '<b>'+txt+'</b>'}
String boldRedTitle	(String txt) 	{ return '<span style="color:#ff0000"><b>'+txt+'</b></span>'}

def getDevices() {
    def body = [:]
        body.put("method", "Home.getDeviceList") 

    log.info "Polling API to retrieve devices..."   
    
    state.speakerhub = null    
    
	try {         
        def object = pollAPI(body,"YoLink™ Device Service","App")
         
        if (object) {
                def code = object.code
                def curtime = object.time                    
                def msgid = object.msgid
                def method = object.method    
                def desc = object.desc 
           
                state.deviceName = [:]
                state.deviceType = [:]    
                state.deviceToken = [:]  
                state.deviceId = [:]
                state.modelName = [:]
	            
	            def responseValues=[]              
                    
                if (object.data.devices instanceof Collection) { 
					responseValues=object.data.devices           
                    logDebug("Parsing multiple devices: ${responseValues}")
				} else {
					responseValues[0]=object.data.devices                    
                    logDebug("Parsing single device: ${responseValues}")
				}                
                           
				responseValues.each { device ->
					if (device.name) {
						log.info "Found '${device.name}', type=${device.type}"                        
                        
                        def dni = device.deviceId
                                      
						state.deviceName[dni] = device.name                        
                        state.deviceType[dni] = device.type
                        state.deviceToken[dni] = device.token
                        state.deviceId[dni] = device.deviceId   
                        state.modelName[dni] = device.modelName  //New response 02/09/2024
					}
				}                   
			} else { 			               
				log.error "getDeviceList failed"	                
			} 
    
		
	} catch (groovyx.net.http.HttpResponseException e) {	
            log.error "Error executing getDeviceList, Exception: $e"
			if (e?.statusCode == UNAUTHORIZED()) { 
				log.warn "Unauthorized Request. Insure your access credentials match those in your YoLink mobile app"
			}         
	}
    
    return state.deviceName
}     

def pollAPI(body, name=null, type=null){
    def rc=null	
    def retry=3
    
    logDebug("pollAPI(${body})")
        
    while ((rc == null) && (retry>0)) {      
        def headers = [:]
        headers.put("Authorization", "Bearer ${state.access_token}") 
                    
        def Params = [
	      	 uri     : apiURL(),
             headers : headers,
             body    : body 
             ]     
        
        logDebug("Attempting to poll API using parameters: ${Params}")
        
	    try {     
		     httpPostJson(Params) { resp ->
			 	if (resp.data) {                    
					logDebug("API Response: ${resp.data}")
                                 
                   	def object = resp.data
                    
                    def code = object.code                  
                    def desc = object.desc  
                    
                    if ((!desc) || (code==desc)) {
                        desc = translateCode(code)
                        logDebug("Translated Response: ${desc}")
                    }  
                    
                    switch (code) {
                    case "000000": //Success
                         logDebug("Polling of API completed successfully")
                 	     rc = object
                         break;
                        
                    case "020104": // Device is busy, try again later
                         logDebug("Polling failed because device is busy")
                 	     if (retry>0) {
                              pauseExecution(5000)
                              retry = retry - 1  
                              logDebug("Polling failed because device is busy. Attempting to retry, Retries=${retry}")
                         } else {          
                              log.error "Polling failed because device is busy and retry failed. Final error status code: $e.statusCode"
                              log.error "Request: $Params"  
                              retry = -1 // Don't retry
                              rc = object
                         }     
                         break;    
                    
                    case "000201": //Cannot connect to the device    
                    case "000203": //Cannot connect to the device
                         log.warn "Device '${name}' (Type=${type}) is offline"  
                         rc = object
                         break;
                        
                    case "000103": //Device token is invalid
                         if (retry>0) {
                              log.error "${name}'s device token is invalid. Attempting to refreshing the token and re-poll API"
                              getDeviceToken(body.targetDevice)
                              body.token = state.deviceToken[body.targetDevice]
                              retry = 1 // Retry once
                         } else {          
                              log.error "${name}'s device token is invalid. Attempt to refreshing device token and re-poll API failed. Final error status code: $e.statusCode"
                              log.error "Request: $Params"  
                         }                          
                         break;       
                        
                    case "010104": //Invalid request: The token is expired
                         if (retry>0) {
                              retry = retry - 1  
                              logDebug('Request token expired. Attempting to refreshing access token and re-poll API, Retries=${retry}')  
                              refreshAuthToken()                                         
                         } else {          
                              log.error "Request token expired. Attempt to refreshing access token and re-poll API failed. Final error status code: $e.statusCode"
                              log.error "Request: $Params"  
                              retry = -1 // Don't retry
                         }                          
                         break;    
                        
                   default:
                         log.error "Polling of API failed: $desc"
                         log.error "Request: $Params" 
                         rc = object
                         break;
                   }
                } else {
                     log.error "Polling of API failed: No response returned" 
                     log.error "Request: $Params" 
                     rc = object                     
                }  
		    } /* end http post */
	    } catch (groovyx.net.http.HttpResponseException e) {	
            log.error "HTTP Response Exception: $e"
            if (e?.statusCode) { 
			    if (e?.statusCode == UNAUTHORIZED()) { 
                    if (retry>0) {
                      retry = retry - 1  
                      logDebug('Request was unauthorized. Attempting to refreshing access token and re-poll API, Retries=${retry}')  
                      refreshAuthToken()                                         
                    } else {          
                      log.error "Request was unauthorized. Attempt to refreshing access token and re-poll API failed. Final error status code: $e.statusCode"
                      log.error "Request: $Params"  
                      retry = -1 // Don't retry
                    } 
                } else {    
                    log.error "pollAPI() error status code: $e.statusCode"
                    log.error "Request: $Params"
                    retry = -1 // Don't retry
	    		}            
            }
        } catch (java.net.SocketTimeoutException e) {
            log.error "pollAPI() HTTP socket timed out"
            log.error "Request: $Params"
            retry = retry - 1
        } catch (org.apache.http.conn.ConnectTimeoutException e) {
            log.error "pollAPI() HTTP connection timed out"
            log.error "Request: $Params"
            retry = retry - 1            
	    } catch(Exception e) {
            log.error "pollAPI() Exception: $e"
            log.error "Request: $Params"
            retry = -1 // Don't retry
        }
    }  //End While 
    
	return rc
}

@Field static final Map APIErrorCodes = [
    "000000":"Success",
    "000101":"Cannot connect to the Hub",
    "000102":"Hub cannot respond to this command",
    "000103":"Device token is invalid",
    "000104":"Hub token is invalid",
    "000105":"redirect_uri cannot be null",
    "000106":"client_id is invalid",
    "000201":"Cannot connect to the device",
    "000202":"Device cannot response to this command",
    "000203":"Cannot connect to the device",
    "010000":"Service is not available, try again later",
    "010001":"Internal connection is not available, try again later",
    "010101":"Invalid request: CSID is invalid",
    "010102":"Invalid request: SecKey is invalid",
    "010103":"Invalid request: Authorization is invalid",
    "010104":"Invalid request: The token is expired",
    "010200":"Invalid data packet: params is not valid",
    "010201":"Invalid data packet: time can not be null",
    "010202":"Invalid data packet: method  can not be null",
    "010203":"Invalid data packet: method is not supported",       //Was returned trying to set WiFi
    "010204":"Invalid data packet",
    "010300":"This interface is restricted to access",
    "010301":"Access denied due to limits reached. Please retry later",
    "020100":"The device is already bound by another user",
    "020101":"The device does not exist",
    "020102":"Device mask error",
    "020103":"The device is not supported",
    "020104":"Device is busy, try again later",
    "020105":"Unable to retrieve device",
    "020201":"No devices were searched",
    "030101":"No data found",
    "999999":"UnKnown Error, Please email to yaochi@yosmart.com"
]

def translateCode(code) {
    def translation = APIErrorCodes["$code"]
    
    if (translation) {
        return translation
    } else {
        log.error" ${code} is an undefined API error code"
        return " ${code} is an undefined API error code"
    }
}

@Field static final Map batteryLevels = [
    "0":0,
    "1":25,
    "2":50,
    "3":75,
    "4":100
]

def batterylevel(level) {
    if (!level) {return 0}

    def battery = batteryLevels["${level}"]

    if (battery) {
        return battery
    } else {
        log.error" ${level} is an undefined battery level"
        return "-1"
    }
}

def relayState(value) {    
    switch (value) {
       case "open":
         return "on"
         break;
       case "closed":
         return "off" 
         break;
       case "close":
         return "off" 
         break; 
       default:
         return value
         break;
    }
}  

def convertTemperature(temperature, scale=null) {
    def temperatureScale = scale ?: settings.temperatureScale
    
    if (temperatureScale == "F") {
        return celsiustofahrenheit(temperature).toDouble().round(1)        
    } else { 
        if (temperatureScale == "C") {
            return temperature
        } else {    
            log.error "convertTemperature(): Temperature scale (${temperatureScale}) is invalid"
            return "error"
        }      
    }    
}

def temperatureScale() {
    return settings.temperatureScale
}
    
def celsiustofahrenheit(celsius) {return ((celsius * 9 / 5) + 32)} 

def scheduledDays(weekdays) {
   def days    
    
   def daysB = Integer.toBinaryString(weekdays.toInteger())                   
   def ndx = daysB.length()    
   
   while (ndx >0) {
     def bit = daysB.substring(ndx-1,ndx)     
     
     if (bit == "1") {  
        days = commaConcat(days,scheduledDay(daysB.length()-ndx))
     }    
       
     ndx--  
   }    
    
   logDebug("Scheduled Days: ${days}")
   return days 
}

private NameToDNI(nameDNI) {
   log.info nameDNI 
   def DNI = nameDNI.substring(0,nameDNI.indexOf("="))  
   log.warn DNI      
   return DNI 
}

@Field static final Map weekdays = [
    "0":"Sun",
    "1":"Mon",
    "2":"Tue",
    "3":"Wed",
    "4":"Thu",
    "5":"Fri",
    "6":"Sat"
]

def scheduledDay(dayndx) {
    def weekday = weekdays["${dayndx}"]

    if (weekday) {
        return weekday
    } else {
        log.error" ${dayndx} is an invalid weekday index"
        return "???"
    }
}

def commaConcat(oldvalue,newvalue) {
    if (!oldvalue) {
      oldvalue = newvalue  
    } else {
      oldvalue = oldvalue + "," + newvalue    
    }    
    return oldvalue
}

boolean validBoolean(setting,value) {            // Allow any one of ON, OFF, TRUE, FALSE, YES, NO
   value = value ?: ""   
   value=value.toUpperCase()
   def rc = null
            
   if (("${value}" == "TRUE") || ("${value}" == "ON") || ("${value}" == "YES")) {
       rc = true 
   } else {     
       if (("${value}" == "FALSE") || ("${value}" == "OFF") || ("${value}" == "NO")) {
         rc = false
       } else {    
         log.error "${setting}(${value}) is invalid. Use 'FALSE', 'TRUE', 'OFF', 'ON', 'NO' or 'YES'"         
       }    
   } 
   return rc
}

// Temperature/Humidity and Temperature Devices are both reported as "THSensor"
// If device doesn't return humidity values, assume is doesn't have that capability
def getTHSensorDriver(name,type,token,devId) {
    def driver = "THSensor"
	try {  
        def request = [:]
            request.put("method", "THSensor.getState")                   
            request.put("targetDevice", "${devId}") 
            request.put("token", "${token}") 
        
        def object = pollAPI(request, name, type)
         
        if (object) {
            logDebug("getTHSensorDriver()> pollAPI() response: ${object}")     
            
            if (object.code == "000000") {             
                def lowHumidity = object.data.state.alarm.lowHumidity                             
                def highHumidity = object.data.state.alarm.highHumidity
                def humidity = object.data.state.humidity            
                def humidityCorrection = object.data.state.humidityCorrection        
                def humidityLimitMax = object.data.state.humidityLimit.max
                def humidityLimitMin = object.data.state.humidityLimit.min 
                
                if (lowHumidity == false && highHumidity == false && humidity == 0 && humidityCorrection == 0 && humidityLimitMax == 0 && humidityLimitMin == 0) {   //Assume device doesn't have humidity sensor
                    log.info "$name appears to be a temperature only sensor."
                    driver = "Temperature Sensor"
                } else {
                    log.info "$name appears to be a temperature and humidity sensor."
                }    
            } else {  //Error
                log.error "API polling returned error: $object.code - " + translateCode(object.code)               
            }     
        } else {
            log.error "No response from API request"
        } 
	} catch (groovyx.net.http.HttpResponseException e) {	
            if (e?.statusCode == UNAUTHORIZED_CODE) { 
                log.error("getTHSensorDriver() - Unauthorized Exception")
            } else {
				log.error("getTHSensorDriver() - Exception $e")
			}                 
	}
    
	return driver
}  

// Devices YoLink™ LeakSensor (YS7903-UC) and YoLink™ LeakSensor3 (YS7904-UC) are both reported as "LeakSensor"
// If the device doesn't support 'supportChangeMode' then it's a YS7903-UC
def getLeakSensorDriver(name,type,token,devId) {
    def driver = "LeakSensor"
	try {  
        def request = [:]
            request.put("method", "LeakSensor.getState")                   
            request.put("targetDevice", "${devId}") 
            request.put("token", "${token}") 
        
        def object = pollAPI(request, name, type)
         
        if (object) {
            logDebug("getLeakSensorDriver()> pollAPI() response: ${object}")     
            
            if (object.code == "000000") {             
                def supportChangeMode = object.data.state.supportChangeMode                            
                                
                if (supportChangeMode == true) {  
                    log.info "$name appears to be a LeakSensor3 (YS7904-UC) sensor."
                    driver = "LeakSensor3"
                } else {
                    log.info "$name appears to be a LeakSensor (YS7903-UC) sensor."
                }    
            } else {  //Error
                log.error "API polling returned error: $object.code - " + translateCode(object.code)               
            }     
        } else {
            log.error "No response from API request"
        } 
	} catch (groovyx.net.http.HttpResponseException e) {	
            if (e?.statusCode == UNAUTHORIZED_CODE) { 
                log.error("getLeakSensorDriver() - Unauthorized Exception")
            } else {
				log.error("getLeakSensorDriver() - Exception $e")
			}                 
	}
    
	return driver
}  

// YoLink™ MultiOutlet (YS6801-UC) and Smart Outdoor Plug (YS6802-UC/SH-18A) are both reported as "MultiOutlet"
// If device only returns delays on 2 channels (0 and 1), assume it's a Smart Outdoor Plug 
def getMultiOutletDriver(name,type,token,devId) {
    def driver = "MultiOutlet"
	try {  
        def request = [:]
            request.put("method", "MultiOutlet.getState")                   
            request.put("targetDevice", "${devId}") 
            request.put("token", "${token}") 
        
        def object = pollAPI(request, name, type)
         
        if (object) {
            logDebug("getMultiOutletDriver() - pollAPI() response: ${object}")     
            
            if (object.code == "000000") {             
                def delay = object.data?.delays[2]                                            
                
                if (delay==null) { 
                    log.info "$name appears to be a Smart Outdoor Plug."
                    driver = "Smart Outdoor Plug"
                } else {
                    log.info "$name appears to be a MultiOutlet Device."
                }    
            } else {  //Error
                log.error "API polling returned error: $object.code - " + translateCode(object.code)               
            }     
        } else {
            log.error "No response from API request"
        } 
	} catch (groovyx.net.http.HttpResponseException e) {	
            if (e?.statusCode == UNAUTHORIZED_CODE) { 
                log.error("getMultiOutletDriver() - Unauthorized Exception")
            } else {
				log.error("getMultiOutletDriver() - Exception $e")
			}                 
	}
    
	return driver
}   

def logDebug(msg) {
    if (debugging == "True"){
       log.debug msg
    }   
}    

def appendData(olddata, newdata) {
    if (olddata == null) {
       olddata = newdata.plus("\r\n")
    } else {
       olddata = olddata.plus(newdata).plus("\r\n") 
    } 
    return olddata
}

Boolean writeFile(fName, fData) {
    String encodedString = location.hub
    encodedString = encodedString.bytes.encodeBase64().toString()
    
    def String Top = """--${encodedString}\r\nContent-Disposition: form-data; name="uploadFile"; filename="${fName}"\r\nContent-Type:text/html\r\n\r\n"""
    def String Bottom = """\r\n\r\n--${encodedString}\r\nContent-Disposition: form-data; name="folder"\r\n\r\n--${encodedString}--"""
    
    ByteArrayOutputStream OutputStream = new ByteArrayOutputStream()
    
    OutputStream.write(Top.getBytes("UTF-8"))
    OutputStream.write(fData.getBytes("UTF-8"))
    OutputStream.write(Bottom.getBytes("UTF-8"))

    byte[] Body = OutputStream.toByteArray();  
    try {
		def params = [
			uri: 'http://127.0.0.1:8080',
			path: '/hub/fileManager/upload',
			query: [
				'folder': '/'
			],
            requestContentType: "application/octet-stream",
			headers: [
				'Content-Type': "multipart/form-data; boundary=$encodedString"
			], 
            body: Body,
			timeout: 300,
			ignoreSSLIssues: true
		]
		httpPost(params) { resp ->
            return resp.data.success == 'true' ? true:false
		}
	}
	catch (e) {
		log.error "Error writing file $fName: ${e}"
	}
	return false
}
