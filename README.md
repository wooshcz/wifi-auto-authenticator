# WiFi Auto Authenticator
This repository contains the source code for the Androoid App available for download on [Google Play](https://play.google.com/store/apps/details?id=com.woosh.wifiautoauth).

## Features
This app simplifies and automates the login process on certain types of the Cisco Web Auth captive portal that is used on some WiFi networks.
The app detects changes of the network connectivity and resolves the captive portal redirection on a selected WiFi network with an automatic login function.

## Rooted phones
The app can automatically switch on/off mobile data on your phone during automatic login to bypass the built-in captive portal detection inside some versions of the Android OS. There is also a manual toggle to turn the mobile data on and off.

## Permissions
- Coarse Location is necessary on Android Marshmallow to perform WiFi scanning.
- It is necessary to access and change Wireless State to perform the scanning and turn on the WiFi.

## Disclaimer
This app stores the Web Auth portal password in plain text because it is then sent in the HTTP request during automatic login procedure. Be aware that the HTTP requests do not have to be secure if the Web Auth is running on un-encrypted HTTP. This app does not validate the server SSL certificates.
