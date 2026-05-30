# Shepherd

A guided **Controlled Remote Viewing (CRV)** session app for Android, with live
biometrics from a MOYOUNG-V2 / P58 smartwatch, binaural-beat induction audio, spoken
stage guidance, and session notes. A native port of the `crv_research` Python tool into
a single Android Studio project.

> CRV and binaural entrainment are unproven practices. Shepherd is a personal research
> and meditation tool, **not** a medical device, and its biometric readings are not
> clinical-grade.

## What it does

- **Guided protocol** — the full six-stage SRI/Ingo-Swann CRV sequence (baseline →
  Focus-10 induction → Stages I–VI → cool-down), with an optional Focus 21–27 Monroe
  "journey" extension. Three timing tiers (Short ~58 min, Standard ~103 min, Long
  ~152 min).
- **Binaural induction** — a real-time stereo audio engine (`AudioTrack`) that
  synthesizes per-Focus carrier + delta-f beat, 2nd/3rd/5th harmonics, amplitude
  modulation, and a Voss-McCartney pink-noise bed, crossfading smoothly between Focus
  levels. **Headphones required.**
- **Guidance modes** — what gets voiced during each stage is selectable. Four modes
  ship built-in (*Passive intelligence gathering*, *Active application*, *Associative /
  ARV (predictive)*, *Beginner coaching*); you can also author your own per-stage text in
  an editor and save it as a reusable mode. An active mode replaces the built-in stage
  descriptions (the short cue and Focus announcement still play).
- **Add your own target** — pick an image from the device; it's sealed into the pool with
  a generated coordinate just like a fetched target, and becomes the next session's
  target, revealed at the end like any other.
- **Spoken guidance** — Android TTS announces each Focus level and stage cue, and
  vocalizes the target coordinate digit-by-digit during the active viewing stages.
  **Voices are selectable**: Shepherd lists the device's installed voices most-natural
  first, tagging Google's **Neural** voices (the most human-sounding; some need network)
  and high-quality **Enhanced** on-device voices. You can preview any voice, pick one,
  and tune speed and pitch — all persisted. The most natural results come from Google's
  neural voices (Settings → Accessibility → Text-to-speech → Google → install voice
  data); Shepherd auto-selects the best available on first launch.
- **Blind target pool** — build a pool of real image targets pulled from Wikimedia
  Commons Featured Pictures. Each is sealed (encrypted) on device and assigned an
  SRI-style coordinate (`XXX-XXXX X`). At session start one is drawn at random and stays
  hidden; after the session you go through **commit-then-reveal** (notes are
  SHA-256-hashed before reveal so later edits are detectable), **rank the target blind
  against 3 decoys**, then **reveal** the actual image, description, and attribution.
  Without a pool the app falls back to a bare generated coordinate.
- **Live biometrics** — connects to the watch over BLE and shows heart rate, SpO₂, and
  blood pressure; auto-measures vitals at session start and lets you re-measure on
  demand. HR is polled continuously (the P58 doesn't auto-stream it).
- **Notes & export** — per-stage prompts and free-form notes are accumulated into a
  Markdown transcript shown at the end.
- **Custom backgrounds** — choose what the glass cards float over: built-in designs
  (twilight gradient, a bundled geometric blue, aurora, mesh-glow, starfield) drawn
  procedurally or from a bundled image, or load your own photo from the gallery (it's
  copied into app storage and shown with a legibility scrim). Picked in Settings,
  persisted across launches.
- **Glass UI** — frosted, semi-transparent cards float over the chosen background;
  content scrolls beneath a translucent top bar (logo + title) and a glass bottom
  navigation bar. Everything is grouped into four tabs — **Session, Targets, Modes,
  Settings** — and a first-launch **onboarding** flow sets a display name and generates
  a private viewer ID.
- **Dark / light theme** — a toggle in Settings, persisted with DataStore.
- **Shepherd branding** — a custom vector logo (a shepherd's crook within a guiding
  ring) used in-app and as the adaptive launcher icon.

## Build & run

1. Open the folder in **Android Studio** (Giraffe+); let Gradle sync.
2. Phone on Android 8.0+ (API 26+), USB debugging on. Press **Run**.
3. Grant Bluetooth permission (or tap *Continue without watch* for an audio-only
   session). Put on **headphones**.
4. Set an intention, pick a protocol tier, optionally connect the watch, and tap
   **Begin Session**.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

JDK 17 (bundled with current Android Studio) required.

## Project layout

```
app/src/main/java/com/shepherd/
├── MainActivity.kt              # permissions + entry point
├── ble/                         # corrected MOYOUNG-V2 / P58 stack
│   ├── Moyoung.kt   MoyoungPacket.kt   P58Manager.kt   BleScanner.kt
├── audio/
│   ├── FocusProfiles.kt         # per-Focus frequencies (ported from audio.py)
│   ├── BinauralEngine.kt        # AudioTrack synthesis: carriers, AM, pink noise
│   └── VoiceEngine.kt           # TTS stage cues + coordinate vocalization
├── session/
│   ├── Protocol.kt              # stages, timing tiers, coordinate generator
│   ├── GuidanceScripts.kt       # built-in modes + user script library
│   ├── Profile.kt               # user display name + generated viewer ID
│   ├── TargetPool.kt            # Wikimedia fetch, image import, encrypted pool, reveal
│   └── SessionEngine.kt         # stage machine, audio/vitals/coord orchestration
└── ui/
    ├── Theme.kt                 # dark + light schemes, Palette helper
    ├── Glass.kt                 # frosted cards, top bar, bottom nav
    ├── Backgrounds.kt           # built-in designs + custom image backgrounds
    ├── ShepherdLogo.kt          # vector brand mark
    ├── MainViewModel.kt         # wiring, profile/onboarding, theme + voice persistence
    └── Screens.kt               # onboarding, tabbed shell, session, editor (Compose)
```

## Watch communication

The BLE layer mirrors the proven `gatttool` driver from the research tool. Crucially it
**does not bond/pair** — the P58 / MOY-DBT5 connects as a plain unbonded LE peripheral
(on Linux: `gatttool -t public --sec-level=medium`, no pairing). Forcing Android bonding
makes these watches refuse to connect, so Shepherd connects directly over
`TRANSPORT_LE`, clears any stale GATT cache, requests a high-priority connection, settles
briefly, then discovers services and enables the notify CCCDs. It uses
write-without-response on the `fee2` command characteristic and sends the verified
trigger frames:

| Action | frame | response |
| ------ | ----- | -------- |
| Heart rate | `fe ea 20 05 6d` | `0x6d {bpm}` |
| SpO₂ | `fe ea 20 06 6b 00` | `0x6b {pct}` |
| Blood pressure | `fe ea 20 08 69 00 00 00` | `0x69 {status, sys, dia}` |

### Troubleshooting "finds the watch but won't connect"

This is almost always a stale pairing/cache from an earlier attempt:

1. **Android Settings → Bluetooth → the watch → Forget / Unpair.** If you (or an older
   build) ever tried to pair it, that half-bond blocks the clean LE connect. Remove it.
2. Toggle Bluetooth off/on, then connect from inside Shepherd (not from system settings).
3. Make sure the watch isn't already connected to its stock app (Da Fit / FitCloudPro) —
   only one central can hold the link.
4. If it connected once and then never again, that's the GATT cache; Shepherd clears it
   automatically on connect now, but a Bluetooth off/on cycle helps the first time.

Because the watch advertises a **public** address and no service-UUID filter is applied,
Shepherd's scanner lists it by name (P58 / MOY*). The Debian laptop works because BlueZ
makes exactly this kind of unbonded public-address LE connection — Shepherd now does the
same thing.

## Notes & caveats

- **Headphones are essential** for the binaural effect; on a speaker the two ears mix
  and the beat disappears.
- The audio engine runs on a background thread feeding a streaming `AudioTrack`; if you
  background the app, Android may throttle it. Keep Shepherd foregrounded during a
  session (a foreground service could be added for full background playback).
- TTS uses Android's on-device engine, with a voice picker (Neural / Enhanced /
  Standard) and speed/pitch tuning. The Python tool's Kokoro voice isn't available
  on-device; for the most natural sound, install Google's neural voices as noted above.
  If only the basic voice appears, the device hasn't downloaded higher-quality voice
  data yet.
