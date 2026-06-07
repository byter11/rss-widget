<p align="center">
<img src="app/src/main/res/playstore-icon.png" width=150>
</p>

<h1 align="center"><b>📱 HomeFeed - RSS Widget</b></h1>
<h4 align="center">A customizable RSS feed widget</h3>
<p align="center">

<div align="center" style="display: flex; justify-content: center; align-items: flex-start; gap: 12px; flex-wrap: wrap;">
  <a href="https://f-droid.org/packages/com.byterdevs.rsswidget">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" width="170" alt="Get it on F-Droid"/>
  </a>
  <a href="https://github.com/byter11/rss-widget/releases">
    <img src="assets/get-it-on-github.png" height="64" alt="Get it on Github" />
  </a>
</div>


## Features

- <b>Multi-Feed Support:</b> Consolidate multiple RSS feeds into a single, unified widget for a centralized news experience.
- <b>Integrated Reader Mode:</b> Access article content through a distraction-free popup, allowing for quick reading without switching applications.
- <b>Rich Media Support:</b> High-quality image integration enhances the visual presentation of your feeds.
- <b>Material You Design:</b> Full support for Material Design 3 and dynamic coloring, automatically adapting to your system wallpaper and theme.

<b>Customization & Controls:</b>

- <b>Visual Flexibility:</b> Adjust widget opacity, customize headers, and toggle the visibility of article descriptions or sources to match your preference.
- <b>Reading Management:</b> Optional dimming for read articles and flexible timestamp formats (relative or absolute).
- <b>Advanced Refreshing:</b> Set custom background refresh intervals or use a manual refresh button, which can be hidden for a more minimalist look.
- <b>Data Persistence:</b> Built for reliability with local data storage and optimized network handling.

## Screenshots

|                        |                        |                        |
|------------------------|------------------------|------------------------|
| ![](fastlane/metadata/android/en-US/images/phoneScreenshots/config.png) | ![](fastlane/metadata/android/en-US/images/phoneScreenshots/dark.png) | ![](fastlane/metadata/android/en-US/images/phoneScreenshots/reader.png)
| ![](fastlane/metadata/android/en-US/images/phoneScreenshots/light.png)  | ![](fastlane/metadata/android/en-US/images/phoneScreenshots/images.png) | ![](fastlane/metadata/android/en-US/images/phoneScreenshots/webview.png)

## Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- Android device or emulator (API 33+)

### Building

Clone the repository and open in Android Studio:

```sh
git clone https://github.com/yourusername/rsswidget.git
cd rsswidget
```

Build and run using Android Studio or:

```sh
./gradlew assembleDebug
```

## Dependencies

- AndroidX Core, AppCompat
- Material Components
- [Rome](https://rometools.github.io/rome/) (RSS parsing)
- [PrettyTime](https://www.ocpsoft.org/prettytime/) (date formatting)

## License

This project is licensed under the [Apache License 2.0](LICENSE).

## Acknowledgements

- [Rome](https://rometools.github.io/rome/)
- [PrettyTime](https://www.ocpsoft.org/prettytime/)
- AndroidX, Material Components
- [Readability.js](https://github.com/mozilla/readability)