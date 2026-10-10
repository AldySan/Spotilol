<div align="center">
  <img src="art/bgwelcome.png" alt="Spotilol" style="width: 100%; max-width: 900px; margin-bottom: 20px; box-shadow: 0 8px 32px rgba(0,0,0,0.5);">
</div>

<h1 align="center">Spotilol</h1>

<p align="center">
  <a href="https://github.com/lyssadev/Spotilol/stargazers">
    <img src="https://img.shields.io/github/stars/lyssadev/Spotilol?style=for-the-badge&logo=starship&labelColor=0d0d0d&color=1DB954" alt="stars"/>
  </a>
  &nbsp;
  <a href="https://github.com/lyssadev/Spotilol/releases">
    <img src="https://img.shields.io/github/downloads/lyssadev/Spotilol/total?style=for-the-badge&logo=download&labelColor=0d0d0d&color=1DB954" alt="downloads"/>
  </a>
  &nbsp;
  <a href="https://github.com/lyssadev/Spotilol/releases/latest">
    <img src="https://img.shields.io/github/v/release/lyssadev/Spotilol?style=for-the-badge&logo=github&labelColor=0d0d0d&color=1DB954" alt="version"/>
  </a>
  &nbsp;
  <a href="https://github.com/lyssadev/Spotilol/forks">
    <img src="https://img.shields.io/github/forks/lyssadev/Spotilol?style=for-the-badge&logo=git&labelColor=0d0d0d&color=1DB954" alt="forks"/>
  </a>
  &nbsp;
  <a href="https://github.com/lyssadev/Spotilol/commits/main">
    <img src="https://img.shields.io/github/last-commit/lyssadev/Spotilol?style=for-the-badge&logo=git&labelColor=0d0d0d&color=1DB954" alt="last commit"/>
  </a>
  &nbsp;
  <a href="https://deepwiki.com/lyssadev/Spotilol">
    <img src="https://deepwiki.com/badge.svg" alt="DeepWiki" style="height: 28px;"/>
  </a>
  &nbsp;
  <a href="https://discord.gg/95dAE2UkqP">
    <img src="https://img.shields.io/badge/Discord-join-1DB954?style=for-the-badge&logo=discord&logoColor=white&labelColor=0d0d0d" alt="discord"/>
  </a>
</p>

<p align="center">
  an Android app that wraps Spotify's web player with built-in adblocking. no root, no shady mods, just your Spotify account on a slick WebView.
</p>

<p align="center">
  ported from smali to clean Kotlin by <strong>lyssadev</strong>, based on deviato's <strong>Spotifuck</strong>. free, open-source, and it just works.
</p>

---



## Download

<div align="center">
  <a href="https://github.com/lyssadev/Spotilol/releases/latest">
    <img src="https://img.shields.io/github/v/release/lyssadev/Spotilol?style=for-the-badge&logo=github&labelColor=0d0d0d&color=1DB954" alt="Download APK"/>
  </a>
</div>

download the `.apk` and install it on your device. you may need to toggle **"Install from unknown sources"** in your Settings.

---

## Preview

<div align="center">
  <img src="art/spotilol_ss1.jpg" alt="screenshot 1" width="30%" style="max-width: 250px; margin: 4px; border-radius: 12px;" />
  <img src="art/spotilol_ss2.jpg" alt="screenshot 2" width="30%" style="max-width: 250px; margin: 4px; border-radius: 12px;" />
  <img src="art/spotilol_ss3.jpg" alt="screenshot 3" width="30%" style="max-width: 250px; margin: 4px; border-radius: 12px;" />
</div>

---

## Features

- blocks audio ads, trackers and telemetry
- media notification with play, pause, skip, seek, like, shuffle and repeat
- lock screen, Bluetooth and headset controls
- home screen widgets for the player and album art
- Android Auto: browse and search your library from the car
- offline downloads via the InnerTube API, with tags and cover art
- multiple account profiles and a sleep timer
- lyrics with five styles and a picture in picture view
- AMOLED dark mode, Material You and accent color themes
- custom CSS, canvas and video podcasts
- mobile layout tweaks and playlist sorting
- wake lock and power save mode
- automatic and manual update checker

---

## Requirements

- Android 9.0+ (API 28)
- a Spotify account
- Google Chrome / WebView (comes with your phone)

---

## Quick Start

install the APK, open it, done. no setup, no certificates, no fuss. it just works out of the box.

---

## Build It Yourself

```bash
git clone https://github.com/lyssadev/Spotilol
cd Spotilol
./gradlew assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Google Services

this project uses Firebase (analytics, crash reporting, performance). to build, you need:

1. create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. register an Android app with package name `com.project.lol`
3. download the `google-services.json` and place it in `app/`

---

## Contributing

contributions are welcome. open issues, throw PRs, suggest stuff. free for all.

---

## Credits

**deviato** reverse-engineered the original Spotifuck. **lyssadev** ported the core logic from smali to Kotlin and maintains this project.

all rights reserved. lyssadev and deviato.