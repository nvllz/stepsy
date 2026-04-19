<div align="center">
  <img src="images/github-banner.png" alt="stepsy banner"/>
</div>

<div align="center">
  
  [![GitHub latest ver](https://img.shields.io/github/v/release/nvllz/stepsy.svg?label=latest&labelColor=A41E84&color=C51684&style=for-the-badge)](https://github.com/nvllz/stepsy/releases/latest)
  [![Downloads](https://img.shields.io/github/downloads/nvllz/stepsy/total?label=downloads&logo=GitHub&link=https%3A%2F%2Fgithub.com%2Fnvllz%2Fstepsy%2Freleases&labelColor=A41E84&color=C51684&style=for-the-badge)](https://github.com/nvllz/stepsy/releases)
  [![License](https://img.shields.io/github/license/nvllz/stepsy.svg?labelColor=A41E84&color=C51684&style=for-the-badge)](LICENSE)
  
</div>

A lightweight step counter that efficiently uses your phone's sensors.

- **Daily Goals & Streaks**: set a daily step target and build consistent walking streaks
- **Milestones**: walk consistently to earn all the badges
- **Privacy-first design**: Stepsy works completely offline with no internet access in any build. All data stays on your device
- **Reliable backups**: advanced auto-backup system will help you avoid data loss and keep your step history safe
- **Widgets & Notifications**: customizable widgets and cozy notifications to keep your progress always at hand
- **Long-term tracking**: easily view and compare your activity across months and years

&nbsp;

|               Feature               |                        FOSS build                        |                                                                  Full build                                                                  |
|:-----------------------------------:|:--------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------:|
|               Privacy               |                      Fully offline                       |                                                                Fully offline                                                                 |
|Proprietary libraries / dependencies |                           None                           |      Google Play Services ([Activity Recognition API](https://developers.google.com/location-context/activity-recognition/)) – optional      |
|       Step counting accuracy        |        Device sensor tracking, moderate accuracy         | Sensor tracking with periodic Google Services calls to locally estimate current activity and filter out false steps (when biking/in vehicle) |
|        Battery optimization         | Highly optimized sensor tracking and database operations |                                    Same as FOSS; API calls should have negligible effect on battery usage                                    |
|            Availability             |              IzzyOnDroid / F-Droid / Github              |                                                         Obtainium / Github releases                                                          |

&nbsp;

💾 [Paseo database to Stepsy migration](https://github.com/nvllz/stepsy/issues/51#issuecomment-3140281948)

## Screenshots

<div align="center">
  <img src="images/1.png" width="23%" alt="screenshot_1" />
  <img src="images/2.png" width="23%" alt="screenshot_2" />
  <img src="images/3.png" width="23%" alt="screenshot_3" />
  <img src="images/4.png" width="23%" alt="screenshot_4" />
</div>

## Download

<div align=center>
  <a href="https://apt.izzysoft.de/packages/com.nvllz.stepsy"><img src="images/badge_izzyondroid.png" width="31%" alt="IzzyOnDroid" /></a>
  <a href="https://f-droid.org/packages/com.nvllz.stepsy/"><img src="images/badge_fdroid.png" width="31%" alt="F-Droid" /></a>
  <a href="https://intradeus.github.io/http-protocol-redirector?r=obtainium://add/github.com/nvllz/stepsy"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/b1c8ac6f2ab08497189721a788a5763e28ff64cd/assets/graphics/badge_obtainium.png" alt="Obtainium" width="31%"></a>
</div>

## Custom intents

You can automate step counting state with apps such as Tasker using broadcast intents.

| Field | Value |
| :---: | :---: |
|Intent type| broadcast|
|Package|com.nvllz.stepsy|
|Activity/Action|com.nvllz.stepsy.action.PAUSE (or RESUME)|

## Dependencies

- [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) - Apache License 2.0
- Google Play Services (full build only, optional)

## Credits

Stepsy is based on [Motionmate](https://github.com/0xf4b1/motionmate) created by [0xf4b1](https://github.com/0xf4b1).
