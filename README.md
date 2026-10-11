# İzmir Keşif

Android app that paints the İzmir streets you have walked or driven through.

- Map: the OpenStreetMap street network of each ilçe, downloaded on the phone from the Overpass API
  and drawn as a dark "network" map. No map tiles, no API keys.
- Tracking: foreground service using the phone's GPS. Every fix is appended to `track.bin`, and the
  path between fixes is matched to street pieces (≤40 m) within ~15–25 m that run in the direction
  of travel. Downloading an ilçe later replays the whole track, so earlier trips get painted too.
- Progress: % of street length per ilçe and per mahalle (OSM admin_level 8/9/10, whichever has
  the most areas). All data stays on the device.

## Build

No Gradle/Android SDK needed (the build machine had no access to Google's SDK servers):

- `aapt2` from the PyPI wheel `aapt2==0.2.1`
- framework classes/resources from Maven Central `org.robolectric:android-all:14-robolectric-10818077` (API 34)
- `com.jakewharton.android.repackaged:dalvik-dx:16.0.1` and `com.android.tools.build:apksig:2.3.0`

Put those in `$TOOLS` (see `build.sh`) and run `./build.sh`. The signing key `keystore.p12`
(alias `kesif`) is not in this repository; it is kept privately. Updates only install over the old version if
signed with the same key.
