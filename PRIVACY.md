# EVDial Privacy Policy

Last updated: 27 November 2026.

EVDial is an Android phone application developed in the public **autonatoke-del/evdial** project. This policy describes the Android client, including its optional F-Droid and Google Play distribution modes. The project's contact channel is [EVDial support issues](https://github.com/autonatoke-del/evdial/issues). The policy does not replace the separate policies of a carrier, SIP provider, browser or external service. Describing a distribution mode does not mean the application has been accepted into that store.

## Contacts and call history

EVDial reads the Android contacts provider after contact access is granted. It uses names, numbers and contact photos for lookup and caller identification. Contact details are not uploaded to an EVDial server by these features. Android or another account provider may separately synchronize the device address book under its own settings.

The app reads the Android call log when it is the selected default phone application and call-log access is permitted, and uses it for recent calls and number lookup. SIP call history is stored separately in private app preferences, with up to 200 entries containing the remote number, call identifier, time, direction and duration. Speed-dial slots and preferences are also stored locally.

Adding or editing a contact opens an Android contact editor. Changing a contact's favorite status requires write access to contacts. Sharing a contact card or text sends the selected data to the application the user chooses. Copy actions put the selected value on the system clipboard.

## Calls, SIP and messages

SIM calls and SMS are handled through Android and the user's mobile carrier. They are not routed through an EVDial-operated telephony service. Carrier billing and data-handling rules apply. EVDial can send an SMS after the user chooses to send it, selects EVDial as the default phone application and grants SMS permission. When EVDial is not the default phone application, it passes the draft and recipient to the user's SMS application for confirmation and delivery. It stores temporary local send-status information; it does not implement an SMS inbox.

SIP is optional and uses the server and account configured by the user. That server receives the information needed for registration and calls, including the SIP identity, destination and network connection information. Call audio is transmitted as part of the call. Once an account is saved, the app can register it when it starts; enabling incoming SIP support keeps a foreground service available for calls. EVDial disables Linphone push notifications and video capture/display in its application code.

The saved SIP password is encrypted with AES-GCM using a key held by Android Keystore. The username, server, transport setting, call history and other preferences do not have additional application-level encryption; they rely on Android's app storage protections. Protecting the saved password does not establish end-to-end encryption for calls. Network protection depends on the configured transport, provider and call negotiation.

Messaging shortcuts pass the chosen number, text or attachments to an installed messaging app, or open Android's share chooser. The receiving app and service control subsequent delivery and processing. EVDial does not have access to that app's conversations merely because a shortcut is present.

## Recordings, files and photos

EVDial does not implement SIM call recording. Its recording feature plays an existing audio file selected by the user. The optional folder feature searches a user-selected local folder for filenames matching a call number and time. Android document permissions can be retained so the file or folder can be opened again. The app stores recording links locally and does not upload the recordings.

A user-selected wallpaper is copied and re-encoded in private app storage; the import does not preserve the source image's EXIF metadata. File attachments are selected through Android's document picker and passed to the chosen app only when the user invokes sharing.

## Charging directory and external services

The bundled charging directory is an offline snapshot of selected Russian Open Charge Map records. Searching that bundled data is local and does not require device location access. See the [dataset attribution](src/main/assets/charging-license.txt).

Map and route buttons open external sites with the selected station coordinates or search text. Web search, charging websites, the project websites and DIAGNOST AI open in an installed browser's tab or browser window. These sites receive normal web requests and can apply their own cookies, accounts and privacy practices. Some linked services are proprietary.

Before the first DIAGNOST AI visit, EVDial explains that it is an external service and records the user's acknowledgement locally. The link does not automatically append contacts, the number in the dialer or call history. Information a user enters on the site is governed by that site's practices.

## Updates and analytics

The website-distributed preview has a user-invoked updater. Checking for updates contacts `https://autonatoke.ru/evdial/`; downloading an update retrieves an APK from that site. The web server receives normal connection information, such as the IP address. EVDial does not implement background update polling or unattended installation.

When built with the F-Droid mode, the website updater is disabled, and the installer permission and update file provider are removed. The update action explains repository-managed updates and can open the installed F-Droid client or the F-Droid website. The selected repository and client have their own network behavior.

In Google Play builds, the website updater, installation permission and update file provider are also disabled. The update action opens the EVDial listing in Google Play or its website after the user taps. Updates are distributed by Google Play under its own policies.

No developer analytics, advertising SDK integration or crash-reporting service is implemented in EVDial's application source. This statement does not describe telemetry independently performed by Android, the mobile carrier, the SIP provider, a browser, or another installed app.

## Permissions and user control

| Access | Purpose in EVDial |
| --- | --- |
| Phone/default phone role | Place SIM calls and show the system call interface |
| Read contacts and call log | Contact lookup, caller identification and recent calls |
| Write contacts | Change a contact's favorite status |
| Send SMS | Send a message explicitly submitted by the user |
| Microphone | Audio during SIP voice calls |
| Notifications/full-screen call UI | Incoming call alerts and call-service controls |
| Foreground service/wake lock | Maintain call-related operation; the Google Play build uses a separate service type for user-enabled SIP registration and incoming-call signalling, without microphone capture while idle |
| Internet | SIP and, in website builds, user-invoked update requests |
| Selected documents | Read a chosen recording, folder, wallpaper or attachment |
| Package installation, website build only | Ask Android to install a downloaded preview update |

The manifest does not request location or camera access. Permissions can be changed in Android settings, and features that need revoked access may stop working. The manifest disables Android application backup. Clearing EVDial's storage or uninstalling it removes its private settings, SIP history, saved credentials and imported wallpaper. It does not delete contacts, the system call log, original recordings or data already sent to an external service.

## Source and questions

Relevant implementation is in [Phone.java](src/main/java/ru/autonatoke/evdial/Phone.java), [ContactsScreen.java](src/main/java/ru/autonatoke/evdial/ContactsScreen.java), [SipHistory.java](src/main/java/ru/autonatoke/evdial/SipHistory.java), [QuickSms.java](src/main/java/ru/autonatoke/evdial/QuickSms.java), [RecordingLink.java](src/main/java/ru/autonatoke/evdial/RecordingLink.java), [WallpaperStore.java](src/main/java/ru/autonatoke/evdial/WallpaperStore.java), [EvLinks.java](src/main/java/ru/autonatoke/evdial/EvLinks.java) and [AppUpdates.java](src/main/java/ru/autonatoke/evdial/AppUpdates.java).

Use the [project issue tracker](https://github.com/autonatoke-del/evdial/issues) for questions about this document. Do not post phone numbers, SIP credentials, private contact cards, recordings or other sensitive data in public issues. For security reporting, see [SECURITY.md](SECURITY.md).
