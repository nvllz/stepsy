# MotionMate

[![android](https://github.com/0xf4b1/motionmate/actions/workflows/android.yml/badge.svg)](https://github.com/0xf4b1/motionmate/actions/workflows/android.yml)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/5528e26310324fb7b0908e5fd64911aa)](https://www.codacy.com/manual/tiefensuche/motionmate?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=0xf4b1/motionmate&amp;utm_campaign=Badge_Grade)
[![Maintainability](https://api.codeclimate.com/v1/badges/f1dbf88de7d7702f94e0/maintainability)](https://codeclimate.com/github/0xf4b1/motionmate/maintainability)
[![GitHub release](https://img.shields.io/github/v/release/0xf4b1/motionmate.svg)](https://github.com/0xf4b1/motionmate/releases)
[![GitHub](https://img.shields.io/github/license/0xf4b1/motionmate.svg)](LICENSE)

MotionMate is a very simple and lightweight step counter app for Android that tracks your daily steps and displays the results by selecting a specific day from the calendar view and presents the values in a nice weekly bar chart as well as some other statistics.

It uses the built-in step counter sensor, if it is present on the device and uses a fallback implementation based on the accelerometer sensor otherwise.

It requires no internet access and no special permissions.

## Download

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
      alt="Download from Google Play"
      height="80">](https://play.google.com/store/apps/details?id=com.tiefensuche.motionmate)

or download the apk directly from the [GitHub releases](https://github.com/0xf4b1/motionmate/releases) page.

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