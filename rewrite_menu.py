import re, subprocess
WD = "/opt/data/cache/scratch/pulse"
F = WD + "/app/src/main/java/com/pulseplayer/app/MainActivity.kt"
L = open(F).read().splitlines()

# --- find showMoreMenu start ---
start = None
for i, ln in enumerate(L):
    if ln.strip() == "private fun showMoreMenu() = showPicker(\"Pulse Player \u00b7 More\", arrayOf(":
        start = i; break
assert start is not None, "showMoreMenu start not found"

# --- brace-match to find the true end ---
depth = 0; end = None
for i in range(start, len(L)):
    depth += L[i].count("{") - L[i].count("}")
    if depth == 0 and i > start:
        end = i; break
assert end is not None, "end not found"
print(f"showMoreMenu: lines {start+1}..{end+1} ({end-start+1} lines)")

UNIFIED = '''private fun showMoreMenu() = showPicker("Pulse Player \u00b7 More", arrayOf(
        "Import / file manager", "Online music services", "Malaysian radio", "Audio mixer / effects",
        "Lyrics / subtitles", "World music catalog (Jamendo)", "In-app YouTube / Spotify / JOOX",
        "Live audio spectrum", "Playback preferences", "Bluetooth / Wi-Fi / output",
        "Share current song", "Power saving mode", "Inspect media URL"
    )) { index ->
        when (index) {
            0 -> showPicker("Import music", arrayOf("Pick one or multiple audio files", "Import folder recursively", "View selected files", "Remove imported folders", "Clear picked file list", "Rescan device music")) {
                when (it) {
                    0 -> openFilePicker()
                    1 -> openFolderPicker()
                    2 -> switchPlaylist("PICKED")
                    3 -> clearFolders()
                    4 -> clearPickedFiles()
                    5 -> scanLibrary()
                }
            }
            1 -> showPicker("Online music \u00b7 authorized sources", arrayOf("World music (Jamendo)", "Spotify in-app embed", "JOOX in-app website", "YouTube Music in-app", "YouTube in-app")) {
                when (it) {
                    0 -> showWorldMenu()
                    1 -> openInAppProvider(ProviderMediaUrls.Provider.SPOTIFY)
                    2 -> openInAppProvider(ProviderMediaUrls.Provider.JOOX)
                    3 -> openInAppProvider(ProviderMediaUrls.Provider.YOUTUBE_MUSIC)
                    4 -> openInAppProvider(ProviderMediaUrls.Provider.YOUTUBE)
                }
            }
            2 -> showPicker("Malaysia radio", arrayOf(
                "Play Malaysia radio list", "Auto-discover MY stations & save", "All saved radio", "Add HTTPS stream",
                "RTM official radio (stations)", "BFM 89.9 official player",
                "ERA official player", "MY FM official player"
            )) {
                when (it) {
                    0 -> switchPlaylist("MY RADIO")
                    1 -> discoverMalaysiaRadio()
                    2 -> switchPlaylist("RADIO")
                    3 -> addRadioDialog()
                    4 -> openProvider("RTM Radio Malaysia")
                    5 -> openProvider("BFM 89.9")
                    6 -> openProvider("ERA Malaysia")
                    7 -> openProvider("MY FM")
                }
            }
            3 -> showAudioMenu()
            4 -> showPicker("Lyrics / subtitles", arrayOf("Import local .lrc (synchronized)", "View synchronized lyrics", "Timed subtitles for current track", "View timed subtitles")) {
                when (it) {
                    0 -> importLyrics()
                    1 -> showLyricsDialog()
                    2 -> lyricsForCurrent()
                    3 -> showLyricDialog()
                }
            }
            5 -> showWorldMenu()
            6 -> showPicker("Choose in-app provider", arrayOf("YouTube", "YouTube Music", "Spotify Embed", "JOOX Website")) { which ->
                val provider = listOf(
                    ProviderMediaUrls.Provider.YOUTUBE,
                    ProviderMediaUrls.Provider.YOUTUBE_MUSIC,
                    ProviderMediaUrls.Provider.SPOTIFY,
                    ProviderMediaUrls.Provider.JOOX
                )[which]
                openInAppProvider(provider)
            }
            7 -> requestSpectrum()
            8 -> showPlaybackMenu()
            9 -> showPicker("Connection options", arrayOf("Android Bluetooth settings", "Change audio output", "Android Wi-Fi settings")) {
                when (it) {
                    0 -> openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                    1 -> chooseAudioOutput()
                    2 -> openSystemSettings(Settings.ACTION_WIFI_SETTINGS)
                }
            }
            10 -> shareCurrentTrack()
            11 -> showPowerSavingDialog()
            12 -> Phase1InspectorDialog.show(this)
        }
    }'''

L = L[:start] + UNIFIED.split("\n") + L[end+1:]
open(F, "w").write("\n".join(L) + "\n")
print("rewrote showMoreMenu; new file lines:", len(L))
marks = [x for x in L if re.match(r"^(<<<<<<<|=======|>>>>>>>)", x)]
print("conflict markers remaining in file:", len(marks))
# verify the when-block cases line count
cases = [x for x in L if re.match(r"^\s+(0|1|2|10|11|12) -> ", x) and x.strip().startswith(("0 ->","1 ->","10 ->","11 ->","12 ->"))]
print("top-level case lines found:", len(cases))
