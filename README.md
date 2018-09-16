# Hiper
Fast Voice User Interface (VUI) for your smart devices that works even offline.

This code is meant to be used with [Hiper Android app](https://play.google.com/store/apps/details?id=com.vitorbnc.hiper)

## API
Hiper works with JSON data. When user talks to Hiper, it publishes the following:

`{"stt":"what-the-user-said","appId":"xxxxxxx"}`

When *Stop* button is pressed (red square), Hiper sends a stop command:

`{"cmd":"stop","appId":"xxxxxxx"}`

Each **appId** is unique to an Android device, and should be used to separate user interactions from different devices.

Hiper will only respond to JSON data containig the same appId as its own. Currently, it accepts the following:

* TTS

`{"tts": "what-hiper-should-say", "app_id": "xxxxxxx"}`

* Unmute

Increase device volume

`{"app": "forceUnmute", "app_id": "xxxxxxxx"}`


## Installation
Clone this repository and run *install.sh* or manually install
* mosquitto
* mosquitto-clients (for testing)

And the python package
* paho-mqtt (from pip)

## Testing
Just run file *test_stop.py* with python 3:
`python3 ./test_stop.py`

When you press Stop button in the app, Hiper will speak.

