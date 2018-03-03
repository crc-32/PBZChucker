# PBZChucker
UI to install PBZs using Pebble or Gadgetbridge app
## What it is
An Android app to install Pebble PBZ firmware files using either the official Pebble app or the Gadgetbridge app.

## How it does it
Simply sends the right intent to the app to save you running `am start` or to stop having your file manager fail to open PBZs in the right app

## What it needs to be
A full lightweight firmware flasher that can be independent of any other apps.

## Requirements
### Build
I strongly suggest just using the pre-signed releases, but the app has no special requirements that don't come pre-installed with Android Studio

### App
Either the Gadgetbridge or Pebble app, and a File Manager (If you're on Android 5.0 and above, the file manager is likely to not work and therefore you should just enter the path to the file inside the app itself. See [#3](https://github.com/crc-32/PBZChucker/issues/1))