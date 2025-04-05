# MotionMate

[![android](https://github.com/0xf4b1/motionmate/actions/workflows/android.yml/badge.svg)](https://github.com/0xf4b1/motionmate/actions/workflows/android.yml)
[![GitHub release](https://img.shields.io/github/v/release/0xf4b1/motionmate.svg)](https://github.com/0xf4b1/motionmate/releases)
[![GitHub](https://img.shields.io/github/license/0xf4b1/motionmate.svg)](LICENSE)

MotionMate is a very simple and lightweight step counter app for Android that records your daily steps and presents the results in a nice weekly bar chart along with other statistics.

It uses the built-in step counter sensor if it is present on the device, and otherwise uses a fallback implementation based on the accelerometer sensor.

It supports file-based data import and export and does not require internet access, so all your data is stored on the device only.

## Download

Download the apk directly from the [GitHub releases](https://github.com/0xf4b1/motionmate/releases) page.

## Screenshots

<img src="images/motionmate-screenshot-1.png" width="300"/> <img src="images/motionmate-screenshot-2.png" width="300"/>

## Building

	$ git clone https://github.com/0xf4b1/motionmate && cd motionmate
	$ ./gradlew assembleDebug
	$ adb install app/build/outputs/apk/debug/app-debug.apk

## License

The project is licensed under GPLv3.

## Dependencies

- [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) - Apache License 2.0
