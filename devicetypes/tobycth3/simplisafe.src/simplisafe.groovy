/**
 *  SimpliSafe integration for SmartThings
 *
 *  Copyright 2015 Felix Gorodishter
 *  Modifications by Scott Silence
 *	Modifications by Toby Harris - 2/6/2018
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

preferences {
	input(name: "username", type: "text", title: "Username", required: "true", description: "SimpliSafe Username")
	input(name: "password", type: "password", title: "Password", required: "true", description: "SimpliSafe Password")
	input(name: "ssversion", type: "enum", title: "SimpliSafe Version", required: "true", description: "Alarm system version", options: ["ss1", "ss2", "ss3"])
}

metadata {	
	definition (name: "SimpliSafe", namespace: "tobycth3", author: "Toby Harris") {
		capability "Alarm"
		capability "Polling"
        capability "Contact Sensor"
		capability "Carbon Monoxide Detector"
		capability "Presence Sensor"
		capability "Smoke Detector"
        capability "Temperature Measurement"
        capability "Water Sensor"
		command "home"
		command "away"
		command "off"
		command "update_state"
	}

tiles(scale: 2) {
    multiAttributeTile(name:"alarm", type: "generic", width: 6, height: 4){
        tileAttribute ("device.alarm", key: "PRIMARY_CONTROL") {
            attributeState "off", label:'${name}', icon: "st.security.alarm.off", backgroundColor: "#505050"
            attributeState "home", label:'${name}', icon: "st.Home.home4", backgroundColor: "#00BEAC"
            attributeState "away", label:'${name}', icon: "st.security.alarm.on", backgroundColor: "#008CC1"
			attributeState "pending off", label:'${name}', icon: "st.security.alarm.off", backgroundColor: "#ffffff"
			attributeState "pending away", label:'${name}', icon: "st.Home.home4", backgroundColor: "#ffffff"
			attributeState "pending home", label:'${name}', icon: "st.security.alarm.on", backgroundColor: "#ffffff"
			attributeState "failed set", label:'error', icon: "st.secondary.refresh", backgroundColor: "#d44556"
        }
		
		tileAttribute("device.events", key: "SECONDARY_CONTROL", wordWrap: true) {
			attributeState("default", label:'${currentValue}')
		}
    }	
	
    standardTile("off", "device.alarm", width: 2, height: 2, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
        state ("off", label:"off", action:"off", icon: "st.security.alarm.off", backgroundColor: "#008CC1", nextState: "pending")
        state ("away", label:"off", action:"off", icon: "st.security.alarm.off", backgroundColor: "#505050", nextState: "pending")
        state ("home", label:"off", action:"off", icon: "st.security.alarm.off", backgroundColor: "#505050", nextState: "pending")
        state ("pending", label:"pending", icon: "st.security.alarm.off", backgroundColor: "#ffffff")
	}
	
    standardTile("away", "device.alarm", width: 2, height: 2, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
        state ("off", label:"away", action:"away", icon: "st.security.alarm.on", backgroundColor: "#505050", nextState: "pending") 
		state ("away", label:"away", action:"away", icon: "st.security.alarm.on", backgroundColor: "#008CC1", nextState: "pending")
        state ("home", label:"away", action:"away", icon: "st.security.alarm.on", backgroundColor: "#505050", nextState: "pending")
		state ("pending", label:"pending", icon: "st.security.alarm.on", backgroundColor: "#ffffff")
	}
	
    standardTile("home", "device.alarm", width: 2, height: 2, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
        state ("off", label:"home", action:"home", icon: "st.Home.home4", backgroundColor: "#505050", nextState: "pending")
        state ("away", label:"home", action:"home", icon: "st.Home.home4", backgroundColor: "#505050", nextState: "pending")
		state ("home", label:"home", action:"home", icon: "st.Home.home4", backgroundColor: "#008CC1", nextState: "pending")
		state ("pending", label:"pending", icon: "st.Home.home4", backgroundColor: "#ffffff")
	}
		valueTile("temperature", "device.temperature", width: 2, height: 2, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
			state ("temperature", label:'${currentValue}', unit: "dF", icon: "st.alarm.temperature.normal", 
			backgroundColors: [
			[value: 0, color: "#ffffff"],
			[value: 41, color: "#d44556"],
			[value: 70, color: "#50C65F"],
			[value: 95, color: "#d44556"]
				]
			)
		}
	standardTile("refresh", "device.alarm", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
		state "default", action:"update_state", icon:"st.secondary.refresh"
	}

		main(["alarm"])
		details(["alarm","off", "away", "home", "temperature", "refresh"])
	}
}

def installed() {
  init()
}

def updated() {
  unschedule()
  init()
}
  
def init() {
	log.info "Setting up Schedule (every 5 minutes)..."
runEvery5Minutes(poll)
}

// handle commands
def off() {
	log.info "Setting SimpliSafe mode to 'Off'"
	setState ('off')
}

def home() { 
	log.info "Setting SimpliSafe mode to 'Home'"
	setState ('home')
}

def away() {
	log.info "Setting SimpliSafe mode to 'Away'"
	setState ('away')
}

def update_state() {
	log.info "Refreshing SimpliSafe state..."
	poll()
}

def setState (alState){
	//Check Auth first
	checkAuth()
    def timeout = false;
    
    if (alState == "off")
    {
    	try {
        	httpPost([ uri: getAPIUrl("alarmOff"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8" ])
        } catch (e) {
        	timeout = true;
        	log.debug "Alarm SET to OFF Error: $e"
        }
    }
    else if (alState == "home")
    {
    	try {
        	httpPost([ uri: getAPIUrl("alarmHome"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8" ])
        } catch (e) {
        	timeout = true;
        	log.debug "Alarm SET to HOME Error: $e"
        }
    }
    else if (alState == "away")
    {
    	try {
        	httpPost([ uri: getAPIUrl("alarmAway"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8" ])
        } catch (e) {
        	timeout = true;
        	log.debug "Alarm SET to AWAY Error: $e"
        }
    }
    else
    {
        log.info "Invalid state requested."
    }
    
    //If not a timeout, we can poll immediately, otherwise wait 10 seconds
    if (!timeout) {
    	poll()
    } else {
    	//There was a timeout, so we can't poll right away. Wait 10 seconds and try polling.
    	runIn(10, poll)
    }
}

def poll() {
	//Check Auth first
	checkAuth()

    log.info "Executing polling..."
   
	httpGet ([uri: getAPIUrl("refresh"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8"]) { response ->
        sendEvent(name: "alarm", value: response.data.subscription.location.system.alarmState)
		sendEvent(name: "temperature", value: response.data.subscription.location.system.temperature)
        log.info "Alarm State1: $response.data.subscription.location.system.alarmState"
		log.info "Temperature: $response.data.subscription.location.system.temperature"
    }
    //log.info "Alarm State2: $response"
    //apiLogout()
}

def apiLogin() {
	//Login to the system
    log.info "Executing Login..."
   
   	//Define the login Auth Body and Header Information
    def authBody = [ "grant_type":"password",
    				"device_id":"WebApp",
                    "username":settings.username,
                    "password": settings.password ]                    
    def authHeader = [ "Authorization":"Basic NGRmNTU2MjctNDZiMi00ZTJjLTg2NmItMTUyMWIzOTVkZWQyLjEtMC0wLldlYkFwcC5zaW1wbGlzYWZlLmNvbTo="	]
    
    try {
        httpPost([ uri: getAPIUrl("initAuth"), headers: authHeader, contentType: "application/json; charset=utf-8", body: authBody ]) { response ->
        	state.auth = response.data
            state.auth.respAuthHeader = ["Authorization":state.auth.token_type + " " + state.auth.access_token]
            state.auth.tokenExpiry = now() + 3600000
        }
 	} catch (e) {
    	//state.token = 
    }
    
    //Check for valid UID, and if not get it
    if (!state.uid)
   	{
    	getUserId()
   	}
    
    //Check for valid Subscription ID, and if not get it
    //Might be able to expand this to multiple systems
    if (!state.subscriptionId)
    {
    	getSubscriptionId()
    }
}

def getUserId() {
	//check auth and get uid    
    httpGet ([uri: getAPIUrl("authCheck"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8"]) { response ->
        state.uid = response.data.userId
    }
    log.info "User ID: $state.uid"
}

def getSubscriptionId() {
	//get subscription id
    httpGet ([uri: getAPIUrl("subId"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8"]) { response ->
    	String tsid = response.data.subscriptions.location.sid
		state.subscriptionId = tsid.substring(1, tsid.length() - 1)
    }
    log.info "Subscription ID: $state.subscriptionId"
}

def checkAuth()
{
	log.info "Checking to see if time has expired...."
        
    //If no State Auth, or now Token Expiry, or time has expired, need to relogin
    //log.info "Expiry time: $state.auth.tokenExpiry"
    if (!state.auth || !state.auth.tokenExpiry || now() > state.auth.tokenExpiry) {    
    	log.info"Token Time has expired, excecuting re-login..."
        apiLogin()
    }
    
	//Check Auth
    try {
        httpGet ([uri: getAPIUrl("authCheck"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8"]) { response ->
            return response.status        
        }
    } catch (e) {
        state.clear()
        apiLogin()
        httpGet ([uri: getAPIUrl("authCheck"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8"]) { response ->
            return response.status        
    }
}
}

def apiLogout() {
    httpDelete([ uri: getAPIUrl("initAuth"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8" ]) { response ->
        if (response.status == 200) {
            state.subscriptionId = null
            log.info "Logged out from API."
        }
    }
}

def getTime()
{
	def tDate = new Date()
    return tDate.getTime()
}

def getAPIUrl(urlType) {
	if (urlType == "initAuth")
    {
    	return "https://api.simplisafe.com/v1/api/token"
    }
    else if (urlType == "authCheck")
    {
    	return "https://api.simplisafe.com/v1/api/authCheck"
    }
    else if (urlType == "subId" )
    {
    	return "https://api.simplisafe.com/v1/users/$state.uid/subscriptions?activeOnly=false"
    }
    else if (urlType == "alarmOff" )
    {
    	if (settings.ssversion == "ss3") 
        {
    		return "https://api.simplisafe.com/v1/$settings.ssversion/subscriptions/$state.subscriptionId/state/off"
        }
        else
        {
        	return "https://api.simplisafe.com/v1/subscriptions/$state.subscriptionId/state?state=off"
        }       
    }
    else if (urlType == "alarmHome" )
    {
   		if (settings.ssversion == "ss3") 
        {
    		return "https://api.simplisafe.com/v1/$settings.ssversion/subscriptions/$state.subscriptionId/state/home"
        }
        else
        {
        	return "https://api.simplisafe.com/v1/subscriptions/$state.subscriptionId/state?state=home"
        }
    }
    else if (urlType == "alarmAway" )
    {
   		if (settings.ssversion == "ss3") 
        {
    		return "https://api.simplisafe.com/v1/$settings.ssversion/subscriptions/$state.subscriptionId/state/away"
        }
        else
        {
        	return "https://api.simplisafe.com/v1/subscriptions/$state.subscriptionId/state?state=away"
        }
    }
    else if (urlType == "refresh")
    {
    	return "https://api.simplisafe.com/v1/subscriptions/$state.subscriptionId/"
    }
    else
    {
    	log.info "Invalid URL type"
    }
}
