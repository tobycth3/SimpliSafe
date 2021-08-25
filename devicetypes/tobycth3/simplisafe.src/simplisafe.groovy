/**
 * SimpliSafe integration for Hubitat
 *
 * Copyright 2015 Felix Gorodishter
 * Modifications by Scott Silence
 *	Modifications by Toby Harris - 5/25/2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 *
 */

preferences {
 input(name: "username", type: "text", title: "Username", required: "true", description: "SimpliSafe Username")
 input(name: "password", type: "password", title: "Password", required: "true", description: "SimpliSafe Password")
 input(name: "ssversion", type: "enum", title: "SimpliSafe Version", required: "true", description: "Alarm system version", options: ["ss1", "ss2", "ss3"])
 input(name: "interval", type:"enum", title: "Refresh Interval", required: true, options: ["1", "5", "10", "15", "30"])
 input("controlEnable", "bool", title: "Allow arm/disarm control from this app?")
 input("debugEnable", "bool", title: "Enable debug logging?")
}

metadata {
 definition(name: "SimpliSafe", namespace: "tobycth3", author: "Toby Harris") {
 capability "Alarm"
 capability "Polling"
 capability "Contact Sensor"
 capability "Carbon Monoxide Detector"
 capability "Presence Sensor"
 capability "Smoke Detector"
 capability "Temperature Measurement"
 capability "Water Sensor"
 capability "Sensor"
 command "off"
 command "home"
 command "away"
 attribute "messages", "string"
 attribute "status", "string"
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
 state.clear()
 if (debugEnable) runIn(1800, logsOff)
 log.info "Setting up schedule every ${settings.interval} minute(s)..."
 if (settings.interval == "1") runEvery1Minute(poll)
 if (settings.interval == "5") runEvery5Minutes(poll)
 if (settings.interval == "10") runEvery10Minutes(poll)
 if (settings.interval == "15") runEvery15Minutes(poll)
 if (settings.interval == "30") runEvery30Minutes(poll)
}

// handle commands
def off() {
 log.info "Setting SimpliSafe mode to 'Off'"
 setState ('off')
}

def home() {
 log.info "Setting SimpliSafe mode to 'Home'"
 setState('home')
}

def away() {
 log.info "Setting SimpliSafe mode to 'Away'"
 setState('away')
}

def setState(alState) {
 def timeout = false;
if (controlEnable) {
 if (alState == "off") {
 try {
 httpPost([uri: getAPIUrl("alarmOff"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8"]) {
 resp ->
 }
 } catch (e) {
 timeout = true;
		log.error "setState(off) something went wrong: $e"
 }
 } else if (alState == "home") {
 try {
 httpPost([uri: getAPIUrl("alarmHome"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8"]) {
 resp ->
 }
 } catch (e) {
 timeout = true;
		log.error "setState(home) something went wrong: $e"
 }
 } else if (alState == "away") {
 try {
 httpPost([uri: getAPIUrl("alarmAway"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8"]) {
 resp ->
 }
 } catch (e) {
 timeout = true;
		log.error "setState(away) something went wrong: $e"
 }
 } else {
 log.error "setState() invalid state requested."
 }

 //If not a timeout, we can poll immediately, otherwise wait 10 seconds
 if (!timeout) {
 poll()
 } else {
 //There was a timeout, so we can't poll right away. Wait 10 seconds and try polling.
 runIn(10, poll)
 }
 } else {
 log.warn "Arm/disarm control not allowed, check settings"
 }
}

def poll() {
 if (state.auth) {
 if (debugEnable) log.info "Executing polling..."
 try {
 httpGet([uri: getAPIUrl("refresh"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8"]) {
 response ->

 //Check alarm state
 sendEvent(name: "alarm", value: response.data.subscription.location.system.alarmState.toLowerCase())
 sendEvent(name: "status", value: response.data.subscription.location.system.alarmState.toLowerCase())
 if (debugEnable) log.info "Alarm State1: $response.data.subscription.location.system.alarmState"

 //Check temperature
 if (response.data.subscription.location.system.temperature != null) {
 sendEvent(name: "temperature", value: response.data.subscription.location.system.temperature, unit: "dF")
 if (debugEnable) log.info "Temperature: $response.data.subscription.location.system.temperature"
 }

 //Set presence && virtual acceleration
 def alarm_state = response.data.subscription.location.system.alarmState.toLowerCase()
 if (alarm_state != device.currentValue("alarm")) {

 def alarm_presence = ['off': 'present', 'home': 'present', 'away': 'not present']
 sendEvent(name: 'presence', value: alarm_presence.getAt(alarm_state))

 /*
 def alarm_acceleration = ['off':'inactive', 'home':'active', 'away':'active']
 sendEvent(name: 'acceleration', value: alarm_acceleration.getAt(alarm_state))
 */

 }

 //Check messages
 if (settings.ssversion == "ss3") {
 if (response.data.subscription.location.system.messages[0] != null) {
 sendEvent(name: "status", value: "alert")
 sendEvent(name: "messages", value: response.data.subscription.location.system.messages[0].text)
 if (debugEnable) log.info "Messages: ${response.data.subscription.location.system.messages[0].text}"

 //Check for alerts
 if (response.data.subscription.location.system.messages[0].category == "alarm") {
 sendEvent(name: "contact", value: "open")
 sendEvent(name: "status", value: "alarm")
 if (debugEnable) log.info "Message category: ${response.data.subscription.location.system.messages[0].category}"

 //Carbon Monoxide sensor alerts
 if (response.data.subscription.location.system.messages[0].data.sensorType == "C0 Detector") {
 sendEvent(name: "carbonMonoxide", value: "detected")
 sendEvent(name: "status", value: "carbonMonoxide")
 if (debugEnable) log.info "Message sensor: ${response.data.subscription.location.system.messages[0].data.sensorType}"
 }

 //Smoke sensor alerts
 if (response.data.subscription.location.system.messages[0].data.sensorType == "Smoke Detector") {
 sendEvent(name: "smoke", value: "detected")
 sendEvent(name: "status", value: "smoke")
 if (debugEnable) log.info "Message sensor: ${response.data.subscription.location.system.messages[0].data.sensorType}"
 }

 /*
 Temperature sensor alerts
 if (response.data.subscription.location.system.messages[0].data.sensorType == "Freeze Sensor")
 {
 sendEvent(name: "temperature", value: "??")
 sendEvent(name: "status", value: "temperature")
 if(debugEnable)log.info "Message sensor: ${response.data.subscription.location.system.messages[0].data.sensorType}"
 }
 */

 //Water sensor alerts
 if (response.data.subscription.location.system.messages[0].data.sensorType == "Water Sensor") {
 sendEvent(name: "water", value: "wet")
 sendEvent(name: "status", value: "water")
 if (debugEnable) log.info "Message sensor: ${response.data.subscription.location.system.messages[0].data.sensorType}"
 }
 }
 } else {
 sendEvent(name: "contact", value: "closed")
 sendEvent(name: "messages", value: "none")
 sendEvent(name: "carbonMonoxide", value: "clear")
 sendEvent(name: "smoke", value: "clear")
 //sendEvent(name: "temperature", value: "??")
 sendEvent(name: "water", value: "dry")
 if (debugEnable) log.info "Messages: ${response.data.subscription.location.system.messages}"
 }
 }
 }
 } catch (e) {
 log.error "poll() something went wrong: $e"
 state.auth = null
 apiLogin()
 }
 } else {
 apiLogin()
 }
}

def apiLogin() {
 if (debugEnable) log.info "Executing login..."

 def params = [
 uri: 'https://api.simplisafe.com',
 path: '/v1/api/token',
 contentType: "application/json",
 body: ["grant_type": "password",
 "username": settings.username,
 "password": settings.password,
 "client_id": device.id,
 "device_id": "WebApp"
 ]
 ]
 asynchttpPost(apiLoginHandler, params)
}

def apiLoginHandler(response, data) {
 if (response.hasError()) {
 if (debugEnable) log.trace "response received error: ${response.getErrorMessage()}"
 if (debugEnable) log.trace "error response data: ${response.getErrorData()}"
 state.errorData = parseJson(response.errorData)
 if (state.errorData.error == "mfa_required") {
 if (debugEnable) log.info state.errorData.mfa_token
 state.mfa_token = state.errorData.mfa_token
 mfaAuth()
 }
 } else {
 state.remove("errorData")
 state.remove("mfa_token")
 state.remove("mfa_challenge")
 initAuth()
 }
}

def mfaAuth() {
 if (debugEnable) log.info "Executing MFA login..."
 if (!state.mfa_challenge) {
 def params = [
 uri: 'https://api.simplisafe.com',
 path: '/v1/api/mfa/challenge',
 contentType: "application/json",
 body: ["challenge_type": "oob",
 "client_id": device.id,
 "mfa_token": state.mfa_token
 ]
 ]
 asynchttpPost(mfaAuthHandler, params)
 } else {
 log.warn "MFA pending, check email"
 }
}

def mfaAuthHandler(response, data) {
 if (response.hasError()) {
 if (debugEnable) log.trace "response received error: ${response.getErrorMessage()}"
 } else {
 log.warn "MFA sent, check email"
 state.mfa_challenge = "sent"
 }
}

def initAuth() {
 //Login to the system
 if (!state.errorData) {
 def authBody = ["grant_type": "password",
 "username": settings.username,
 "password": settings.password,
 "client_id": device.id,
 "device_id": "WebApp"
 ]

 try {
 httpPost([uri: getAPIUrl("initAuth"), contentType: "application/json; charset=utf-8", body: authBody]) {
 response ->
 state.auth = response.data
 state.auth.respAuthHeader = ["Authorization": state.auth.token_type + " " + state.auth.access_token]
 //state.auth.tokenExpiry = now() + 18000
 }
 } catch (e) {
 log.error "initAuth() something went wrong: $e"
 }

 //Check for valid UID, and if not get it
 if (!state.uid) {
 getUserId()
 }

 //Check for valid Subscription ID, and if not get it
 //Might be able to expand this to multiple systems
 if (!state.subscriptionId) {
 getSubscriptionId()
 }
 poll()
 }
}

def getUserId() {
 //check auth and get uid 
 httpGet([uri: getAPIUrl("authCheck"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8"]) {
 response ->
 state.uid = response.data.userId
 }
 if (debugEnable) log.info "User ID: $state.uid"
}

def getSubscriptionId() {
 //get subscription id
 httpGet([uri: getAPIUrl("subId"), headers: state.auth.respAuthHeader, contentType: "application/json; charset=utf-8"]) {
 response ->
 String tsid = response.data.subscriptions.location.sid
 state.subscriptionId = tsid.substring(1, tsid.length() - 1)
 }
 if (debugEnable) log.info "Subscription ID: $state.subscriptionId"
}

def getTime() {
 def tDate = new Date()
 return tDate.getTime()
}

def getAPIUrl(urlType) {
 if (urlType == "initAuth") {
 return "https://api.simplisafe.com/v1/api/token"
 } else if (urlType == "authCheck") {
 return "https://api.simplisafe.com/v1/api/authCheck"
 } else if (urlType == "subId") {
 return "https://api.simplisafe.com/v1/users/$state.uid/subscriptions?activeOnly=false"
 } else if (urlType == "alarmOff") {
 if (settings.ssversion == "ss3") {
 return "https://api.simplisafe.com/v1/$settings.ssversion/subscriptions/$state.subscriptionId/state/off"
 } else {
 return "https://api.simplisafe.com/v1/subscriptions/$state.subscriptionId/state?state=off"
 }
 } else if (urlType == "alarmHome") {
 if (settings.ssversion == "ss3") {
 return "https://api.simplisafe.com/v1/$settings.ssversion/subscriptions/$state.subscriptionId/state/home"
 } else {
 return "https://api.simplisafe.com/v1/subscriptions/$state.subscriptionId/state?state=home"
 }
 } else if (urlType == "alarmAway") {
 if (settings.ssversion == "ss3") {
 return "https://api.simplisafe.com/v1/$settings.ssversion/subscriptions/$state.subscriptionId/state/away"
 } else {
 return "https://api.simplisafe.com/v1/subscriptions/$state.subscriptionId/state?state=away"
 }
 } else if (urlType == "refresh") {
 return "https://api.simplisafe.com/v1/subscriptions/$state.subscriptionId/"
 } else if (urlType == "events") {
 return "https://api.simplisafe.com/v1/subscriptions/$state.subscriptionId/events?numEvents=1"
 } else {
 log.error "Invalid URL type"
 }
}

void logsOff() {
 device.updateSetting("debugEnable", [value: "false", type: "bool"])
}
