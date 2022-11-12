/***
 *  YoLink™ Thermostat (YS4002-UC)
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
 *  2.0.0: First Release
 */

import groovy.json.JsonSlurper

def clientVersion() {return "2.0.0"}

preferences {
    input title: "Driver Version", description: "YoLink™ Thermostat (YS4002-UC) v${clientVersion()}", displayDuringSetup: false, type: "paragraph", element: "paragraph"
    input title: "Please donate", description: "<p>Please support the development of this application and future drivers. This effort has taken me hundreds of hours of research and development. <a href=\"https://www.paypal.com/donate/?business=HHRCLVYHR4X5J&no_recurring=1\">Donate via PayPal</a></p>", displayDuringSetup: false, type: "paragraph", element: "paragraph"
}

metadata {
    definition (name: "YoLink Thermostat Device", namespace: "srbarcus", author: "Steven Barcus") {   
        capability "Initialize"
		capability "Polling"	
        capability "Thermostat"
        capability "ThermostatCoolingSetpoint"
        capability "ThermostatFanMode"
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatMode"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"   
                                 
        command "debug", [[name:"debug",type:"ENUM", description:"Display debugging messages", constraints:["true", "false"]]]
        command "reset"        
        command "setEcoMode", [[name:"setEcoMode",type:"ENUM", description:"Turn ECO mode on or off", constraints:["on", "off"]]]
        command "schedules", [[name:"schedules",type:"ENUM", description:"Run or hold the programed schedules", constraints:["run", "hold"]]]   
        command "refreshSchedules"
        command "pollingOverride", [[name:"pollingOverride",type:"ENUM", description: "Device polling interval", constraints: ["None", "1 Minute", "2 Minutes", "3 Minutes", "4 Minutes", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes"]]] 
        
        attribute "online", "String"
        attribute "firmware", "String"  
        attribute "signal", "String"
        attribute "lastResponse", "String" 
                
        attribute "schedules", "String"
        
        attribute "sundaywake", "String"
	    attribute "sundaywakelow", "String"
        attribute "sundaywakehigh", "String"
	    attribute "sundayleave", "String"
        attribute "sundayleavelow", "String"
	    attribute "sundayleavehigh", "String"
        attribute "sundayreturn", "String"
	    attribute "sundayreturnlow", "String"
        attribute "sundayreturnhigh", "String"
	    attribute "sundaysleep", "String"
        attribute "sundaysleeplow", "String"
        attribute "sundaysleephigh", "String"

        attribute "mondaywake", "String"
        attribute "mondaywakelow", "String"
	    attribute "mondaywakehigh", "String"
        attribute "mondayleave", "String"
	    attribute "mondayleavelow", "String"
        attribute "mondayleavehigh", "String"
	    attribute "mondayreturn", "String"
        attribute "mondayreturnlow", "String"
        attribute "mondayreturnhigh", "String"
        attribute "mondaysleep", "String"
        attribute "mondaysleeplow", "String"
        attribute "mondaysleephigh", "String"
                   
        attribute "tuesdaywake", "String"
        attribute "tuesdaywakelow", "String"
		attribute "tuesdaywakehigh", "String"
        attribute "tuesdayleave", "String"
		attribute "tuesdayleavelow", "String"
        attribute "tuesdayleavehigh", "String"
		attribute "tuesdayreturn", "String"
		attribute "tuesdayreturnlow", "String"
	    attribute "tuesdayreturnhigh", "String"
        attribute "tuesdaysleep", "String"
		attribute "tuesdaysleeplow", "String"
        attribute "tuesdaysleephigh", "String"
                   
        attribute "wednesdaywake", "String"
		attribute "wednesdaywakelow", "String"
        attribute "wednesdaywakehigh", "String"
		attribute "wednesdayleave", "String"
        attribute "wednesdayleavelow", "String"
		attribute "wednesdayleavehigh", "String"
        attribute "wednesdayreturn", "String"
        attribute "wednesdayreturnlow", "String"
        attribute "wednesdayreturnhigh", "String"
        attribute "wednesdaysleep", "String"
        attribute "wednesdaysleeplow", "String"
        attribute "wednesdaysleephigh", "String"

        attribute "thursdaywake", "String"
        attribute "thursdaywakelow", "String"
        attribute "thursdaywakehigh", "String"
        attribute "thursdayleave", "String"
        attribute "thursdayleavelow", "String"
        attribute "thursdayleavehigh", "String"
        attribute "thursdayreturn", "String"
        attribute "thursdayreturnlow", "String"
        attribute "thursdayreturnhigh", "String"
        attribute "thursdaysleep", "String"
        attribute "thursdaysleeplow", "String"
        attribute "thursdaysleephigh", "String"
                   
        attribute "fridaywake", "String"
        attribute "fridaywakelow", "String"
        attribute "fridaywakehigh", "String"
        attribute "fridayleave", "String"
        attribute "fridayleavelow", "String"
        attribute "fridayleavehigh", "String"
        attribute "fridayreturn", "String"
        attribute "fridayreturnlow", "String"
        attribute "fridayreturnhigh", "String"
        attribute "fridaysleep", "String"
        attribute "fridaysleeplow", "String"
        attribute "fridaysleephigh", "String"

	    attribute "saturdaywaketext", "String"
	    attribute "saturdayleavetext", "String"
	    attribute "saturdayreturntext", "String"
	    attribute "saturdaysleeptext", "String"
        attribute "sundaywaketext", "String"
	    attribute "sundayleavetext", "String"
        attribute "sundayreturntext", "String"
	    attribute "sundaysleeptext", "String"
        attribute "mondaywaketext", "String"
        attribute "mondayleavetext", "String"
	    attribute "mondayreturntext", "String"
        attribute "mondaysleeptext", "String"
        attribute "tuesdaywaketext", "String"
        attribute "tuesdayleavetext", "String"
		attribute "tuesdayreturntext", "String"
        attribute "tuesdaysleeptext", "String"
        attribute "wednesdaywaketext", "String"
		attribute "wednesdayleavetext", "String"
        attribute "wednesdayreturntext", "String"
        attribute "wednesdaysleeptext", "String"
        attribute "thursdaywaketext", "String"
        attribute "thursdayleavetext", "String"
        attribute "thursdayreturntext", "String"
        attribute "thursdaysleeptext", "String"
        attribute "fridaywaketext", "String"
        attribute "fridayleavetext", "String"
        attribute "fridayreturntext", "String"
        attribute "fridaysleeptext", "String"
	    attribute "saturdaywaketext", "String"
	    attribute "saturdayleavetext", "String"
	    attribute "saturdayreturntext", "String"
	    attribute "saturdaysleeptext", "String"
                
        attribute "ecoMode", "String" 
        attribute "pollingOverride", "String"
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

def initialize() {
    if (state.pollingOverride != "None") {
        pollingOverride(state.pollingOverride)    
    }    
}

def uninstalled() {
   log.warn "Device '${state.name}' (Type=${state.type}) has been uninstalled"     
 }

def pollingOverride(value) {
   logDebug("pollingOverride(${value})")  
   rememberState("pollingOverride",value) 
   log.info "Scheduling device polling for every ${value}"  
   internalPoll()  
}

def internalPoll() {
   logDebug("Running internal poll")   
   poll(null, true)
   
   unschedule() 
    
   if (state.pollingOverride != "None") {
        def interval = state.pollingOverride
        interval = (interval.replace(" Minutes", "").replace(" Minute", "").toInteger()) * 60       
        runIn(interval, internalPoll) 
    }  
 }

def poll(force=null, internalPoll=null) {
    if ((force == true) || (internalPoll == true) || (state.pollingOverride == "None")) {
        if (internalPoll == null) { logDebug("Running external poll") }
        if (force == null) {
          def min_interval = 10                  // To avoid unecessary load on YoLink servers, limit rate of polling
	      def min_time = (now()-(min_interval * 1000))
	      if ((state?.lastPoll) && (state?.lastPoll > min_time)) {
             log.warn "Polling interval of once every ${min_interval} seconds exceeded, device was not polled."	    
             return     
           } 
        }    
    
        getDevicestate() 
    
        runIn(2, getSchedules)    
    
        state.lastPoll = now()    
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

def refreshSchedules() {getSchedules()}

def getSchedules(){
   def request = [:] 
   request.put("method", "Thermostat.getSchedules")                  
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
    
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
        def swState
         
        if (object) {
            logDebug("getSchedules(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {               
                lastResponse("Success")
                try {           
                   def sunday = object.data.sches[0]
                   def monday = object.data.sches[1]
                   def tuesday = object.data.sches[2]
                   def wednesday = object.data.sches[3]
                   def thursday = object.data.sches[4]
                   def friday = object.data.sches[5]
                   def saturday = object.data.sches[6]

                   logDebug("Parsed: Saturday=$saturday")    
                   logDebug("Parsed: Friday=$friday")                      
                   logDebug("Parsed: Thursday=$thursday")
                   logDebug("Parsed: Wednesday=$wednesday")                       
                   logDebug("Parsed: Tuesday=$tuesday")                       
                   logDebug("Parsed: Monday=$monday")    
                   logDebug("Parsed: Sunday=$sunday")      
                   
                   def sundaywake = Date.parse('H:m', sunday[0].time).format('HH:mm') 
                   def sundaywakelow = (parent.convertTemperature(sunday[0].lowTemp).toDouble()).round(0)
                   def sundaywakehigh =(parent.convertTemperature(sunday[0].highTemp).toDouble()).round(0)

                   def sundayleave = Date.parse('H:m', sunday[1].time).format('HH:mm')  
                   def sundayleavelow = (parent.convertTemperature(sunday[1].lowTemp).toDouble()).round(0)
                   def sundayleavehigh =(parent.convertTemperature(sunday[1].highTemp).toDouble()).round(0)

                   def sundayreturn = Date.parse('H:m', sunday[2].time).format('HH:mm')                     
                   def sundayreturnlow = (parent.convertTemperature(sunday[2].lowTemp).toDouble()).round(0)
                   def sundayreturnhigh =(parent.convertTemperature(sunday[2].highTemp).toDouble()).round(0)

                   def sundaysleep = Date.parse('H:m', sunday[3].time).format('HH:mm')                     
                   def sundaysleeplow = (parent.convertTemperature(sunday[3].lowTemp).toDouble()).round(0)
                   def sundaysleephigh =(parent.convertTemperature(sunday[3].highTemp).toDouble()).round(0)

                   logDebug("Sunday: Wake=$sundaywake (${sundaywakelow}°/${sundaywakehigh}°),  Leave=$sundayleave (${sundayleavelow}°/${sundayleavehigh}°),  Return=$sundayreturn (${sundayreturnlow}°/${sundayreturnhigh}°),  Sleep=$sundaysleep (${sundaysleeplow}°/${sundaysleephigh}°)")     
                  
                   def mondaywake = Date.parse('H:m', monday[0].time).format('HH:mm') 
                   def mondaywakelow = (parent.convertTemperature(monday[0].lowTemp).toDouble()).round(0)
                   def mondaywakehigh =(parent.convertTemperature(monday[0].highTemp).toDouble()).round(0)

                   def mondayleave = Date.parse('H:m', monday[1].time).format('HH:mm')  
                   def mondayleavelow = (parent.convertTemperature(monday[1].lowTemp).toDouble()).round(0)
                   def mondayleavehigh =(parent.convertTemperature(monday[1].highTemp).toDouble()).round(0)

                   def mondayreturn = Date.parse('H:m', monday[2].time).format('HH:mm')                     
                   def mondayreturnlow = (parent.convertTemperature(monday[2].lowTemp).toDouble()).round(0)
                   def mondayreturnhigh =(parent.convertTemperature(monday[2].highTemp).toDouble()).round(0)

                   def mondaysleep = Date.parse('H:m', monday[3].time).format('HH:mm')                     
                   def mondaysleeplow = (parent.convertTemperature(monday[3].lowTemp).toDouble()).round(0)
                   def mondaysleephigh =(parent.convertTemperature(monday[3].highTemp).toDouble()).round(0)
                   
                   logDebug("Monday: Wake=$mondaywake (${mondaywakelow}°/${mondaywakehigh}°),  Leave=$mondayleave (${mondayleavelow}°/${mondayleavehigh}°),  Return=$mondayreturn (${mondayreturnlow}°/${mondayreturnhigh}°),  Sleep=$mondaysleep (${mondaysleeplow}°/${mondaysleephigh}°)")     
                 
                   def tuesdaywake = Date.parse('H:m', tuesday[0].time).format('HH:mm') 
                   def tuesdaywakelow = (parent.convertTemperature(tuesday[0].lowTemp).toDouble()).round(0)
                   def tuesdaywakehigh =(parent.convertTemperature(tuesday[0].highTemp).toDouble()).round(0)

                   def tuesdayleave = Date.parse('H:m', tuesday[1].time).format('HH:mm')  
                   def tuesdayleavelow = (parent.convertTemperature(tuesday[1].lowTemp).toDouble()).round(0)
                   def tuesdayleavehigh =(parent.convertTemperature(tuesday[1].highTemp).toDouble()).round(0)

                   def tuesdayreturn = Date.parse('H:m', tuesday[2].time).format('HH:mm')                     
                   def tuesdayreturnlow = (parent.convertTemperature(tuesday[2].lowTemp).toDouble()).round(0)
                   def tuesdayreturnhigh =(parent.convertTemperature(tuesday[2].highTemp).toDouble()).round(0)

                   def tuesdaysleep = Date.parse('H:m', tuesday[3].time).format('HH:mm')                     
                   def tuesdaysleeplow = (parent.convertTemperature(tuesday[3].lowTemp).toDouble()).round(0)
                   def tuesdaysleephigh =(parent.convertTemperature(tuesday[3].highTemp).toDouble()).round(0)
                   
                   logDebug("Tuesday: Wake=$tuesdaywake (${tuesdaywakelow}°/${tuesdaywakehigh}°),  Leave=$tuesdayleave (${tuesdayleavelow}°/${tuesdayleavehigh}°),  Return=$tuesdayreturn (${tuesdayreturnlow}°/${tuesdayreturnhigh}°),  Sleep=$tuesdaysleep (${tuesdaysleeplow}°/${tuesdaysleephigh}°)")     
                 
                   def wednesdaywake = Date.parse('H:m', wednesday[0].time).format('HH:mm') 
                   def wednesdaywakelow = (parent.convertTemperature(wednesday[0].lowTemp).toDouble()).round(0)
                   def wednesdaywakehigh =(parent.convertTemperature(wednesday[0].highTemp).toDouble()).round(0)

                   def wednesdayleave = Date.parse('H:m', wednesday[1].time).format('HH:mm')  
                   def wednesdayleavelow = (parent.convertTemperature(wednesday[1].lowTemp).toDouble()).round(0)
                   def wednesdayleavehigh =(parent.convertTemperature(wednesday[1].highTemp).toDouble()).round(0)

                   def wednesdayreturn = Date.parse('H:m', wednesday[2].time).format('HH:mm')                     
                   def wednesdayreturnlow = (parent.convertTemperature(wednesday[2].lowTemp).toDouble()).round(0)
                   def wednesdayreturnhigh =(parent.convertTemperature(wednesday[2].highTemp).toDouble()).round(0)

                   def wednesdaysleep = Date.parse('H:m', wednesday[3].time).format('HH:mm')                     
                   def wednesdaysleeplow = (parent.convertTemperature(wednesday[3].lowTemp).toDouble()).round(0)
                   def wednesdaysleephigh =(parent.convertTemperature(wednesday[3].highTemp).toDouble()).round(0)

                   logDebug("Wednesday: Wake=$wednesdaywake (${wednesdaywakelow}°/${wednesdaywakehigh}°),  Leave=$wednesdayleave (${wednesdayleavelow}°/${wednesdayleavehigh}°),  Return=$wednesdayreturn (${wednesdayreturnlow}°/${wednesdayreturnhigh}°),  Sleep=$wednesdaysleep (${wednesdaysleeplow}°/${wednesdaysleephigh}°)")     
                 
                   def thursdaywake = Date.parse('H:m', thursday[0].time).format('HH:mm') 
                   def thursdaywakelow = (parent.convertTemperature(thursday[0].lowTemp).toDouble()).round(0)
                   def thursdaywakehigh =(parent.convertTemperature(thursday[0].highTemp).toDouble()).round(0)

                   def thursdayleave = Date.parse('H:m', thursday[1].time).format('HH:mm')  
                   def thursdayleavelow = (parent.convertTemperature(thursday[1].lowTemp).toDouble()).round(0)
                   def thursdayleavehigh =(parent.convertTemperature(thursday[1].highTemp).toDouble()).round(0)

                   def thursdayreturn = Date.parse('H:m', thursday[2].time).format('HH:mm')                     
                   def thursdayreturnlow = (parent.convertTemperature(thursday[2].lowTemp).toDouble()).round(0)
                   def thursdayreturnhigh =(parent.convertTemperature(thursday[2].highTemp).toDouble()).round(0)

                   def thursdaysleep = Date.parse('HH:m', thursday[3].time).format('HH:mm')                     
                   def thursdaysleeplow = (parent.convertTemperature(thursday[3].lowTemp).toDouble()).round(0)
                   def thursdaysleephigh =(parent.convertTemperature(thursday[3].highTemp).toDouble()).round(0)
                 
                   logDebug("Thursday: Wake=$thursdaywake (${thursdaywakelow}°/${thursdaywakehigh}°),  Leave=$thursdayleave (${thursdayleavelow}°/${thursdayleavehigh}°),  Return=$thursdayreturn (${thursdayreturnlow}°/${thursdayreturnhigh}°),  Sleep=$thursdaysleep (${thursdaysleeplow}°/${thursdaysleephigh}°)")     
                 
                   def fridaywake = Date.parse('H:m', friday[0].time).format('HH:mm') 
                   def fridaywakelow = (parent.convertTemperature(friday[0].lowTemp).toDouble()).round(0)
                   def fridaywakehigh =(parent.convertTemperature(friday[0].highTemp).toDouble()).round(0)

                   def fridayleave = Date.parse('H:m', friday[1].time).format('HH:mm')  
                   def fridayleavelow = (parent.convertTemperature(friday[1].lowTemp).toDouble()).round(0)
                   def fridayleavehigh =(parent.convertTemperature(friday[1].highTemp).toDouble()).round(0)

                   def fridayreturn = Date.parse('H:m', friday[2].time).format('HH:mm')                     
                   def fridayreturnlow = (parent.convertTemperature(friday[2].lowTemp).toDouble()).round(0)
                   def fridayreturnhigh =(parent.convertTemperature(friday[2].highTemp).toDouble()).round(0)

                   def fridaysleep = Date.parse('H:m', friday[3].time).format('HH:mm')                     
                   def fridaysleeplow = (parent.convertTemperature(friday[3].lowTemp).toDouble()).round(0)
                   def fridaysleephigh =(parent.convertTemperature(friday[3].highTemp).toDouble()).round(0)
                   
                   logDebug("Friday: Wake=$fridaywake (${fridaywakelow}°/${fridaywakehigh}°),  Leave=$fridayleave (${fridayleavelow}°/${fridayleavehigh}°),  Return=$fridayreturn (${fridayreturnlow}°/${fridayreturnhigh}°),  Sleep=$fridaysleep (${fridaysleeplow}°/${fridaysleephigh}°)")     
                 
		           def saturdaywake = Date.parse('H:m', saturday[0].time).format('HH:mm') 
                   def saturdaywakelow = (parent.convertTemperature(saturday[0].lowTemp).toDouble()).round(0)
                   def saturdaywakehigh =(parent.convertTemperature(saturday[0].highTemp).toDouble()).round(0)

                   def saturdayleave = Date.parse('H:m', saturday[1].time).format('HH:mm')  
                   def saturdayleavelow = (parent.convertTemperature(saturday[1].lowTemp).toDouble()).round(0)
                   def saturdayleavehigh =(parent.convertTemperature(saturday[1].highTemp).toDouble()).round(0)

                   def saturdayreturn = Date.parse('H:m', saturday[2].time).format('HH:mm')                     
                   def saturdayreturnlow = (parent.convertTemperature(saturday[2].lowTemp).toDouble()).round(0)
                   def saturdayreturnhigh =(parent.convertTemperature(saturday[2].highTemp).toDouble()).round(0)

                   def saturdaysleep = Date.parse('H:m', saturday[3].time).format('HH:mm')                     
                   def saturdaysleeplow = (parent.convertTemperature(saturday[3].lowTemp).toDouble()).round(0)
                   def saturdaysleephigh =(parent.convertTemperature(saturday[3].highTemp).toDouble()).round(0)
                    
                   logDebug("saturday: Wake=$saturdaywake (${saturdaywakelow}°/${saturdaywakehigh}°),  Leave=$saturdayleave (${saturdayleavelow}°/${saturdayleavehigh}°),  Return=$saturdayreturn (${saturdayreturnlow}°/${saturdayreturnhigh}°),  Sleep=$saturdaysleep (${saturdaysleeplow}°/${saturdaysleephigh}°)")      
                    
                   saturdaywaketext = Date.parse('H:m', saturdaywake).format('h:mm a')       
	               saturdayleavetext = Date.parse('H:m', saturdayleave).format('h:mm a')
	               saturdayreturntext = Date.parse('H:m', saturdayreturn).format('h:mm a')
	               saturdaysleeptext = Date.parse('H:m', saturdaysleep).format('h:mm a')
                   sundaywaketext = Date.parse('H:m', sundaywake).format('h:mm a')
	               sundayleavetext = Date.parse('H:m', sundayleave).format('h:mm a')
                   sundayreturntext = Date.parse('H:m', sundayreturn).format('h:mm a')
	               sundaysleeptext = Date.parse('H:m', sundaysleep).format('h:mm a')
                   mondaywaketext = Date.parse('H:m', mondaywake).format('h:mm a')
                   mondayleavetext = Date.parse('H:m', mondayleave).format('h:mm a')
	               mondayreturntext = Date.parse('H:m', mondayreturn).format('h:mm a')
                   mondaysleeptext = Date.parse('H:m', mondaysleep).format('h:mm a')
                   tuesdaywaketext = Date.parse('H:m', tuesdaywake).format('h:mm a')
                   tuesdayleavetext = Date.parse('H:m', tuesdayleave).format('h:mm a')
	               tuesdayreturntext = Date.parse('H:m', tuesdayreturn).format('h:mm a')
                   tuesdaysleeptext = Date.parse('H:m', tuesdaysleep).format('h:mm a')
                   wednesdaywaketext = Date.parse('H:m', wednesdaywake).format('h:mm a')
	               wednesdayleavetext = Date.parse('H:m', wednesdayleave).format('h:mm a')
                   wednesdayreturntext = Date.parse('H:m', wednesdayreturn).format('h:mm a')
                   wednesdaysleeptext = Date.parse('H:m', wednesdaysleep).format('h:mm a')
                   thursdaywaketext = Date.parse('H:m', thursdaywake).format('h:mm a')
                   thursdayleavetext = Date.parse('H:m', thursdayleave).format('h:mm a')
                   thursdayreturntext = Date.parse('H:m', thursdayreturn).format('h:mm a')
                   thursdaysleeptext = Date.parse('H:m', thursdaysleep).format('h:mm a')
                   fridaywaketext = Date.parse('H:m', fridaywake).format('h:mm a')
                   fridayleavetext = Date.parse('H:m', fridayleave).format('h:mm a')
                   fridayreturntext = Date.parse('H:m', fridayreturn).format('h:mm a')
                   fridaysleeptext = Date.parse('H:m', fridaysleep).format('h:mm a')
	               saturdaywaketext = Date.parse('H:m', saturdaywake).format('h:mm a')
	               saturdayleavetext = Date.parse('H:m', saturdayleave).format('h:mm a')
	               saturdayreturntext = Date.parse('H:m', saturdayreturn).format('h:mm a')
	               saturdaysleeptext = Date.parse('H:m', saturdaysleep).format('h:mm a')  

                   rememberState("sundaywake",sundaywake)
		           rememberState("sundaywakelow",sundaywakelow)
        		   rememberState("sundaywakehigh",sundaywakehigh)
		           rememberState("sundayleave",sundayleave)
        		   rememberState("sundayleavelow",sundayleavelow)
		           rememberState("sundayleavehigh",sundayleavehigh)
        		   rememberState("sundayreturn",sundayreturn)
		           rememberState("sundayreturnlow",sundayreturnlow)
        		   rememberState("sundayreturnhigh",sundayreturnhigh)
		           rememberState("sundaysleep",sundaysleep)
        		   rememberState("sundaysleeplow",sundaysleeplow)
        		   rememberState("sundaysleephigh",sundaysleephigh) 
        		   rememberState("mondaywake",mondaywake)
        		   rememberState("mondaywakelow",mondaywakelow)
		           rememberState("mondaywakehigh",mondaywakehigh)
        		   rememberState("mondayleave",mondayleave)
		           rememberState("mondayleavelow",mondayleavelow)
        		   rememberState("mondayleavehigh",mondayleavehigh)
		           rememberState("mondayreturn",mondayreturn)
        		   rememberState("mondayreturnlow",mondayreturnlow)
        		   rememberState("mondayreturnhigh",mondayreturnhigh)
        		   rememberState("mondaysleep",mondaysleep)
        		   rememberState("mondaysleeplow",mondaysleeplow)
        		   rememberState("mondaysleephigh",mondaysleephigh) 
        		   rememberState("tuesdaywake",tuesdaywake)
        		   rememberState("tuesdaywakelow",tuesdaywakelow)
		           rememberState("tuesdaywakehigh",tuesdaywakehigh)
        		   rememberState("tuesdayleave",tuesdayleave)
		           rememberState("tuesdayleavelow",tuesdayleavelow)
        		   rememberState("tuesdayleavehigh",tuesdayleavehigh)
		           rememberState("tuesdayreturn",tuesdayreturn)
		           rememberState("tuesdayreturnlow",tuesdayreturnlow)
                   rememberState("tuesdayreturnhigh",tuesdayreturnhigh)
        		   rememberState("tuesdaysleep",tuesdaysleep)
		           rememberState("tuesdaysleeplow",tuesdaysleeplow)
        		   rememberState("tuesdaysleephigh",tuesdaysleephigh)
        		   rememberState("wednesdaywake",wednesdaywake)
		           rememberState("wednesdaywakelow",wednesdaywakelow)
        		   rememberState("wednesdaywakehigh",wednesdaywakehigh)
		           rememberState("wednesdayleave",wednesdayleave)
        		   rememberState("wednesdayleavelow",wednesdayleavelow)
		           rememberState("wednesdayleavehigh",wednesdayleavehigh)
        		   rememberState("wednesdayreturn",wednesdayreturn)
        		   rememberState("wednesdayreturnlow",wednesdayreturnlow)
        		   rememberState("wednesdayreturnhigh",wednesdayreturnhigh)
        		   rememberState("wednesdaysleep",wednesdaysleep)
        		   rememberState("wednesdaysleeplow",wednesdaysleeplow)
        		   rememberState("wednesdaysleephigh",wednesdaysleephigh)
                   rememberState("thursdaywake",thursdaywake)
        		   rememberState("thursdaywakelow",thursdaywakelow)
        		   rememberState("thursdaywakehigh",thursdaywakehigh)
        		   rememberState("thursdayleave",thursdayleave)
        		   rememberState("thursdayleavelow",thursdayleavelow)
        		   rememberState("thursdayleavehigh",thursdayleavehigh)
        		   rememberState("thursdayreturn",thursdayreturn)
        		   rememberState("thursdayreturnlow",thursdayreturnlow)
        		   rememberState("thursdayreturnhigh",thursdayreturnhigh)
        		   rememberState("thursdaysleep",thursdaysleep)
        		   rememberState("thursdaysleeplow",thursdaysleeplow)
        		   rememberState("thursdaysleephigh",thursdaysleephigh) 
        		   rememberState("fridaywake",fridaywake)
        		   rememberState("fridaywakelow",fridaywakelow)
        		   rememberState("fridaywakehigh",fridaywakehigh)
        		   rememberState("fridayleave",fridayleave)
        		   rememberState("fridayleavelow",fridayleavelow)
        		   rememberState("fridayleavehigh",fridayleavehigh)
        		   rememberState("fridayreturn",fridayreturn)
        		   rememberState("fridayreturnlow",fridayreturnlow)
        		   rememberState("fridayreturnhigh",fridayreturnhigh)
        		   rememberState("fridaysleep",fridaysleep)
        		   rememberState("fridaysleeplow",fridaysleeplow)
        		   rememberState("fridaysleephigh",fridaysleephigh)
		           rememberState("saturdaywake",saturdaywake)
		           rememberState("saturdaywakelow",saturdaywakelow)
		           rememberState("saturdaywakehigh",saturdaywakehigh)
		           rememberState("saturdayleave",saturdayleave)
		           rememberState("saturdayleavelow",saturdayleavelow)
		           rememberState("saturdayleavehigh",saturdayleavehigh)
		           rememberState("saturdayreturn",saturdayreturn)
		           rememberState("saturdayreturnlow",saturdayreturnlow)
		           rememberState("saturdayreturnhigh",saturdayreturnhigh)
		           rememberState("saturdaysleep",saturdaysleep)
		           rememberState("saturdaysleeplow",saturdaysleeplow)
		           rememberState("saturdaysleephigh",saturdaysleephigh) 
                    
                   rememberState("saturdaywaketext", saturdaywaketext)   
             	   rememberState("saturdayleavetext", saturdayleavetext)
                   rememberState("saturdayreturntext", saturdayreturntext)
	               rememberState("saturdaysleeptext", saturdaysleeptext)
                   rememberState("sundaywaketext", sundaywaketext)
	               rememberState("sundayleavetext", sundayleavetext)
                   rememberState("sundayreturntext", sundayreturntext)
	               rememberState("sundaysleeptext", sundaysleeptext)
                   rememberState("mondaywaketext", mondaywaketext)
                   rememberState("mondayleavetext", mondayleavetext)
	               rememberState("mondayreturntext", mondayreturntext)
                   rememberState("mondaysleeptext", mondaysleeptext)
                   rememberState("tuesdaywaketext", tuesdaywaketext)
                   rememberState("tuesdayleavetext", tuesdayleavetext)
		           rememberState("tuesdayreturntext", tuesdayreturntext)
                   rememberState("tuesdaysleeptext", tuesdaysleeptext)
                   rememberState("wednesdaywaketext", wednesdaywaketext)
		           rememberState("wednesdayleavetext", wednesdayleavetext)
                   rememberState("wednesdayreturntext", wednesdayreturntext)
                   rememberState("wednesdaysleeptext", wednesdaysleeptext)
                   rememberState("thursdaywaketext", thursdaywaketext)
                   rememberState("thursdayleavetext", thursdayleavetext)
                   rememberState("thursdayreturntext", thursdayreturntext)
                   rememberState("thursdaysleeptext", thursdaysleeptext)
                   rememberState("fridaywaketext", fridaywaketext)
                   rememberState("fridayleavetext", fridayleavetext)
                   rememberState("fridayreturntext", fridayreturntext)
                   rememberState("fridaysleeptext", fridaysleeptext)
	               rememberState("saturdaywaketext", saturdaywaketext)
	               rememberState("saturdayleavetext", saturdayleavetext)
	               rememberState("saturdayreturntext", saturdayreturntext)
	               rememberState("saturdaysleeptext", saturdaysleeptext) 
       
                } catch (groovyx.net.http.HttpResponseException e) {	
                    lastResponse("Parse Exception - $e")                
                    log.error "Parse Exception - $e"

                }       
            } else {                
                if (notConnected(object)) { //Cannot connect to Device - YoLink bug? Device appears offline while state is changing
                   lastResponse("Cannot connect to Device")      
                } else {
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
                }
            }                     
                
	    } else { 			               
            logDebug("getSchedules() failed")	
            lastResponse("getSchedules() failed")     
        }     		
	} catch (e) {	
        log.error "getSchedules() exception: $e"
        lastResponse("Error ${e}")     
	} 
}  

def setEcoMode(value){
   def params = [:] 
   
   params.put("mode", value) 
     
   def request = [:] 
   request.put("method", "Thermostat.setECO")                  
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)             
    
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
        def swState
         
        if (object) {
            logDebug("setEcoMode(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {               
                lastResponse("Success")
                try {           
                   def ecoMode = object.data.eco.mode
                   def signal = object.data.loraInfo.signal     
                    
                   logDebug("Parsed: ecoMode=$ecoMode, Signal=$signal")  
       
                   rememberState("online", "true")
                   rememberState("ecoMode", ecoMode) 
                   rememberState("signal", signal)    
       
                } catch (groovyx.net.http.HttpResponseException e) {	
                    lastResponse("Parse Exception - $e")                
                    log.error "Parse Exception - $e"

                }       
            } else {                
                if (notConnected(object)) { //Cannot connect to Device - YoLink bug? Device appears offline while state is changing
                   lastResponse("Cannot connect to Device")      
                } else {
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
                }
            }                     
                
	    } else { 			               
            logDebug("setEcoMode() failed")	
            lastResponse("setEcoMode() failed")     
        }     		
	} catch (e) {	
        log.error "setEcoMode() exception: $e"
        lastResponse("Error ${e}")     
	} 
}  

def schedules(value) {setThermostat(null,null,null,null,value)}
def auto() {setThermostat(null,null,"auto")}
def cool() {setThermostat(null,null,"cool")}
def emergencyHeat() {setThermostat(null,null,"heat")}
def fanAuto() {setThermostat(null,null,null,"auto") }
def fanCirculate() {setThermostat(null,null,null,"on") }
def fanOn() {setThermostat(null,null,null,"on") }
def heat() {setThermostat(null,null,"heat")}
def off() {setThermostat(null,null,"off")}
def setCoolingSetpoint(temperature) {setThermostat(null,temperature) }            //Cooling setpoint in degrees
def setHeatingSetpoint(temperature) {setThermostat(temperature) }                 //Heating setpoint in degrees
def setThermostatFanMode(fanmode) {
    if (fanmode == "circulate") {fanmode = "on"} 
    setThermostat(null,null,null,fanmode) 
} 
def setThermostatMode(thermostatmode) {
    if (thermostatmode == "emergency heat") {thermostatmode = "heat"} 
    setThermostat(null,null,thermostatmode)
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
        
        def object = parent.pollAPI(request,state.name,state.type)
              
        if (object) {
            logDebug("getDevicestate()> pollAPI() response: ${object}")     
            
            if (successful(object)) {                
                parseDevice(object)                     
                rc = true	
                rememberState("online", "true") 
                lastResponse("Success") 
            } else {  //Error
               if (pollError(object) ) {  //Cannot connect to Device
                 rememberState("switch", "unknown")                      
               }
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
   try {   
       def temperatureScale = object.data.state.tempMode.toUpperCase()
       def temperature = object.data.state.temperature
       def humidity = object.data.state.humidity
       def heatingSetpoint = object.data.state.lowTemp
       def coolingSetpoint = object.data.state.highTemp
       def thermostatMode = object.data.state.mode
       def thermostatFanMode = object.data.state.fan
       def schedules = object.data.state.sche
       def thermostatOperatingState = object.data.state.running
    
       def ecoMode = object.data.eco.mode 

       def firmware = object.data.version.toUpperCase()
   
       def signal = object.data.loraInfo.signal     
       
       humidity = humidity.toDouble().round(1)
                  
       temperature =  parent.convertTemperature(temperature)
       temperature =  temperature.toDouble().round(1)
       heatingSetpoint = (parent.convertTemperature(heatingSetpoint).toDouble()).round(0)
       coolingSetpoint = (parent.convertTemperature(coolingSetpoint).toDouble()).round(0)
       
       if ((thermostatOperatingState == "heat") || (thermostatOperatingState == "cool")) {
          thermostatOperatingState = thermostatOperatingState + "ing"
       }   
                     
       logDebug("Parsed: Temp Scale=$temperatureScale, temperature=$temperature, humidity=$humidity, Cooling Setpoint=$coolingSetpoint, Heating Setpoint=$heatingSetpoint, Mode=$thermostatMode, Fan Mode=$thermostatFanMode, Firmware=$firmware, Signal=$signal") 
       logDebug("Parsed: schedules=$schedules, Operating State=$thermostatOperatingState, ecoMode=$ecoMode, Derived Setpoint=$thermostatSetpoint") 
         
       rememberState("online", "true")
       rememberState("temperatureScale", temperatureScale)
       
       rememberState("temperature", temperature,"°${temperatureScale}")
       rememberState("humidity", humidity)
       rememberState("heatingSetpoint", heatingSetpoint,"°${temperatureScale}")
       rememberState("coolingSetpoint", coolingSetpoint,"°${temperatureScale}")            
       rememberState("thermostatMode", thermostatMode,"°${temperatureScale}")
       rememberState("thermostatFanMode", thermostatFanMode)
       rememberState("schedules", schedules) 
       
       rememberState("thermostatOperatingState", thermostatOperatingState) 
       rememberState("ecoMode", ecoMode) 
       rememberState("firmware", firmware)
       
       rememberState("signal", signal)         
       
   } catch (groovyx.net.http.HttpResponseException e) {	
        lastResponse("parseDevice() Exception - $e")                
        log.error "parseDevice() Exception - $e"

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
         
        case "getState":           
            parseDevice(object)
			break;	
            
		default:
            log.error "Unknown event received: $event"
            log.error "Message received: ${payload}"
			break;
	    }
    }      
}

def setThermostat(lowTemp=null,highTemp=null,mode=null,fan=null,sche=null) {  //lowTemp = Heating Set Point, highTemp = Cooling Set Point
   def params = [:] 
       
   if (lowTemp != null) {
      def setpoint
       
      if (state.temperatureScale == "F") {
        setpoint =  state.coolingSetpoint - 2
      } else {    
        setpoint =  state.coolingSetpoint - convertTemp(2)  
      }
       
       params.put("lowTemp", convertTemp(lowTemp))
   } else {
      if (highTemp != null) {
         def setpoint
       
         if (state.temperatureScale == "F") {
           setpoint =  state.heatingSetpoint + 2
         } else {    
           setpoint =  state.heatingSetpoint + convertTemp(2)  
         }   
        
         params.put("highTemp", convertTemp(highTemp))
      }  
   }
       
   if (mode != null) {params.put("mode", mode)} 
   if (fan != null) {params.put("fan", fan)} 
   if (sche != null) {params.put("sche", sche)}  
     
   def request = [:] 
   request.put("method", "${state.type}.setState")                  
   request.put("targetDevice", "${state.devId}") 
   request.put("token", "${state.token}")     
   request.put("params", params)       
 
   try {         
        def object = parent.pollAPI(request, state.name, state.type)
        def swState
         
        if (object) {
            logDebug("setThermostat(): pollAPI() response: ${object}")  
                              
            if (successful(object)) {               
                lastResponse("Success")
                parseDevice(object) 
                runIn(2, getDevicestate)
             } else {                
                if (notConnected(object)) { //Cannot connect to Device - YoLink bug? Device appears offline while state is changing
                   lastResponse("Cannot connect to Device")      
                } else {
                   rememberState("online","false")                    
                   log.warn "Device '${state.name}' (Type=${state.type}) is offline"  
                   lastResponse("Device is offline")     
                }
            }                     
                
	    } else { 			               
            logDebug("setThermostat() failed")	 
            lastResponse("setThermostat() failed")     
        }     		
	} catch (e) {	
        log.error "setThermostat() exception: $e"
        lastResponse("Error ${e}")             
	} 
}  

def convertTemp(temperature) {
    float celsius = temperature.toFloat()
    if (state.temperatureScale == "F") {
      celsius = celsius - 32
      celsius = celsius * (5/9)
      celsius = celsius.round(1)
    }    
    
    logDebug("convertTemp(): $temperature = $celsius")
    
    return celsius
}

def reset(){          
    state.remove("firmware")
    state.remove("signal")    
    state.remove("online")  
    state.remove("LastResponse")  
    state.remove("schedules") 

    state.remove("ecoMode")    
    
    state.remove("sundaywake")
	state.remove("sundaywakelow")
    state.remove("sundaywakehigh")
	state.remove("sundayleave")
    state.remove("sundayleavelow")
	state.remove("sundayleavehigh")
    state.remove("sundayreturn")
	state.remove("sundayreturnlow")
    state.remove("sundayreturnhigh")
	state.remove("sundaysleep")
    state.remove("sundaysleeplow")
    state.remove("sundaysleephigh")

    state.remove("mondaywake")
    state.remove("mondaywakelow")
	state.remove("mondaywakehigh")
    state.remove("mondayleave")
	state.remove("mondayleavelow")
    state.remove("mondayleavehigh")
	state.remove("mondayreturn")
    state.remove("mondayreturnlow")
    state.remove("mondayreturnhigh")
    state.remove("mondaysleep")
    state.remove("mondaysleeplow")
    state.remove("mondaysleephigh")
                 
    state.remove("tuesdaywake")
    state.remove("tuesdaywakelow")
	state.remove("tuesdaywakehigh")
    state.remove("tuesdayleave")
	state.remove("tuesdayleavelow")
    state.remove("tuesdayleavehigh")
	state.remove("tuesdayreturn")
	state.remove("tuesdayreturnlow")
	state.remove("tuesdayreturnhigh")
    state.remove("tuesdaysleep")
	state.remove("tuesdaysleeplow")
    state.remove("tuesdaysleephigh")
                   
    state.remove("wednesdaywake")
	state.remove("wednesdaywakelow")
    state.remove("wednesdaywakehigh")
	state.remove("wednesdayleave")
    state.remove("wednesdayleavelow")
	state.remove("wednesdayleavehigh")
    state.remove("wednesdayreturn")
    state.remove("wednesdayreturnlow")
    state.remove("wednesdayreturnhigh")
    state.remove("wednesdaysleep")
    state.remove("wednesdaysleeplow")
    state.remove("wednesdaysleephigh")
    
    state.remove("thursdaywake")
    state.remove("thursdaywakelow")
    state.remove("thursdaywakehigh")
    state.remove("thursdayleave")
    state.remove("thursdayleavelow")
    state.remove("thursdayleavehigh")
    state.remove("thursdayreturn")
    state.remove("thursdayreturnlow")
    state.remove("thursdayreturnhigh")
    state.remove("thursdaysleep")
    state.remove("thursdaysleeplow")
    state.remove("thursdaysleephigh")
                   
    state.remove("fridaywake")
    state.remove("fridaywakelow")
    state.remove("fridaywakehigh")
    state.remove("fridayleave")
    state.remove("fridayleavelow")
    state.remove("fridayleavehigh")
    state.remove("fridayreturn")
    state.remove("fridayreturnlow")
    state.remove("fridayreturnhigh")
    state.remove("fridaysleep")
    state.remove("fridaysleeplow")
    state.remove("fridaysleephigh")

	state.remove("saturdaywake")
	state.remove("saturdaywakelow")
	state.remove("saturdaywakehigh")
	state.remove("saturdayleave")
	state.remove("saturdayleavelow")
	state.remove("saturdayleavehigh")
	state.remove("saturdayreturn")
	state.remove("saturdayreturnlow")
	state.remove("saturdayreturnhigh")
	state.remove("saturdaysleep")
	state.remove("saturdaysleeplow")
	state.remove("saturdaysleephigh")
    
    state.remove("saturdaywaketext")
	state.remove("saturdayleavetext")
	state.remove("saturdayreturntext")
	state.remove("saturdaysleeptext")
    state.remove("sundaywaketext")
	state.remove("sundayleavetext")
    state.remove("sundayreturntext")
	state.remove("sundaysleeptext")
    state.remove("mondaywaketext")
    state.remove("mondayleavetext")
	state.remove("mondayreturntext")
    state.remove("mondaysleeptext")
    state.remove("tuesdaywaketext")
    state.remove("tuesdayleavetext")
	state.remove("tuesdayreturntext")
    state.remove("tuesdaysleeptext")
    state.remove("wednesdaywaketext")
	state.remove("wednesdayleavetext")
    state.remove("wednesdayreturntext")
    state.remove("wednesdaysleeptext")
    state.remove("thursdaywaketext")
    state.remove("thursdayleavetext")
    state.remove("thursdayreturntext")
    state.remove("thursdaysleeptext")
    state.remove("fridaywaketext")
    state.remove("fridayleavetext")
    state.remove("fridayreturntext")
    state.remove("fridaysleeptext")
	state.remove("saturdaywaketext")
	state.remove("saturdayleavetext")
	state.remove("saturdayreturntext")
	state.remove("saturdaysleeptext")
    
    rememberState("pollingOverride", "2 Minutes")
    
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

def schedulePolling() {		
    def interval = state.pollingOverride
    if (interval != 0) {
        
    log.trace "Scheduling device polling for every ${interval} minutes"  
    
    //def seconds = 60 * interval
    unschedule()
    //runIn(seconds, pollDevices)  
    
    def pollIntervalCmd = interval.plus("Minutes")
    
    "runEvery${pollIntervalCmd}"(pollDevices)       
    }    
}
