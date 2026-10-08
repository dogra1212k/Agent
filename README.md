# Agent Voice — Android phone control

A lightweight native assistant for Hindi, Hinglish and English voice commands. No server URL, API key, account, root or computer is needed to use the app. Commands are interpreted locally using a defined grammar; this is not a general conversational LLM.

## Install and start

1. Install `Agent-Voice-1.0.0.apk` on Android 8 or newer.
2. Open **Agent Voice**, tap **Tap & speak**, and allow microphone access. Notifications provide a persistent Stop button.
3. For the floating mic, taps, typing and scrolling, choose **Enable screen control**, read the disclosure, and enable **Agent Voice** in Android Accessibility. Return to Agent and tap **Start floating control**.
4. Open another app. Tap the floating dot to speak; drag it to move it. The × button ends the session.

If Android shows a restricted-setting message for this sideloaded app, open Settings → Apps → Agent Voice → top-right menu → **Allow restricted settings**, then return to Accessibility. Menu names vary by phone; Agent cannot grant its own permissions.

## Commands

| Say | Result |
| --- | --- |
| `Chrome kholo aur AI news search karo` | Open Chrome and search |
| `WhatsApp kholo`, `open Settings` | Open installed apps or settings |
| `YouTube par Hindi songs search karo` | YouTube results |
| `maps Delhi` | Map search |
| `numbers dikhao`, then `tap 3` | Number accessible buttons, then tap one |
| `tap Search` | Tap a uniquely matching button |
| `type Namaste Arun` | Replace text in a focused non-password input; does not send |
| `neeche jao`, `scroll up`, `swipe left` | Scroll or swipe |
| `back`, `home`, `recent apps` | System navigation |
| `notifications`, `quick settings` | Open system panels |
| `read screen` | Read accessible text with a locally installed voice |
| `volume up`, `volume down`, `mute`, `unmute` | Media volume |
| `5 minute ka timer lagao` | Open timer app |
| `dial 12345` | Open dialer; user presses Call |
| `time batao`, `madad`, `रुको` | Time, help, stop |

Hindi script works too: `क्रोम खोलो`, `नीचे जाओ`, `नंबर दिखाओ`, `टैप तीन`, `लिखो नमस्ते`.

## Speech and privacy

- Default mode asks the Android speech provider to prefer offline recognition. That provider may use its network service. Agent itself has no `INTERNET` permission or backend.
- **On-device speech only** uses Android's on-device recognizer on Android 12+ and never silently falls back online. It requires a compatible recognizer and downloaded Hindi/English model. Otherwise the app explains the issue and typed commands remain usable.
- Audio feedback uses a voice that does not require a network connection. Without one, responses appear as text.
- **Hands-free session** resumes listening after each response for up to five minutes. It pauses after repeated silence/errors and when the screen locks. It is opt-in, not an always-on wake word.
- Screen text is inspected only for a requested action. No screenshots, audio files, analytics or command history are uploaded. Only language/mode preferences persist. Recent commands remain in process memory until the app process ends.
- Stop is available by voice, floating ×, the home screen and the ongoing notification. Sessions never restart automatically after reboot or process death.

## Limits

Some apps do not expose accessible controls. Password fields and lock screens are excluded. Recognized send/delete/submit buttons require manual confirmation; recognized payment/security buttons stay manual. Label checks cannot identify every possible sensitive button. Numbered targets expire after 45 seconds and are revalidated before use.

The app follows supported commands. It cannot perform every arbitrary task, read inaccessible controls, bypass permissions or unlock a phone. Recognition depends on the phone's speech provider and installed language model.

## Build and checks

Use JDK 17, Gradle 8.9, Android SDK 35 and Android Gradle Plugin 8.7.3:

```sh
gradle testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

GitHub Actions runs parser tests, Android lint and the APK build. An Android 15 emulator checks the home screen, a typed command and foreground-service start/stop, and captures a screenshot and logs. These checks do not test a physical microphone or a particular manufacturer's speech model; voice and Accessibility still need a target-phone check.

The APK is a debug-signed sideload build, not a Play Store release. Independent builds may use a different debug key, in which case Android requires uninstalling the older debug build before installing another.
