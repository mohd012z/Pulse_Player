import re, sys
WD = "/opt/data/cache/scratch/pulse"
F = WD + "/app/src/main/java/com/pulseplayer/app/MainActivity.kt"
L = open(F).read().splitlines()
H1 = open("/opt/data/cache/scratch/h1_MA.kt").read().splitlines()
MN = open("/opt/data/cache/scratch/main_MA.kt").read().splitlines()

def check(n, frag):
    if frag not in L[n-1]:
        raise SystemExit(f"MISMATCH @{n}: want '{frag}' got '{L[n-1][:50]}'")

# confirm fresh-merge markers
for n,fr in [(77,"<<<<<<< HEAD"),(87,"======="),(97,">>>>>>> origin/main"),
             (502,"<<<<<<< HEAD"),(503,"======="),(709,">>>>>>> origin/main"),
             (999,"private fun showMoreMenu"),(1000,"<<<<<<< HEAD"),(1004,"======="),(1007,">>>>>>> origin/main"),
             (1053,"<<<<<<< HEAD"),(1231,"======="),(1255,">>>>>>> origin/main"),
             (1256,".show()"),(1257,"}"),
             (1360,"<<<<<<< HEAD"),(1366,"======="),(1379,">>>>>>> origin/main")]:
    check(n, fr)

# ---- extract #1 method block (clearPickedFiles .. showLyricsDialog) verbatim from clean h1 ----
def method_block(lines, start_name, end_name):
    s = next(i for i,l in enumerate(lines) if re.match(rf"\s*private fun {start_name}\b", l))
    e = next(i for i,l in enumerate(lines) if re.match(rf"\s*private fun {end_name}\b", l))
    depth=0
    for i in range(e, len(lines)):
        depth += lines[i].count("{") - lines[i].count("}")
        if depth==0 and i>e:
            return lines[s:i+1]
    raise SystemExit("end method not closed")
h1_block = method_block(H1, "clearPickedFiles", "showLyricsDialog")
print(f"  h1 method block: {len(h1_block)} lines ({h1_block[0].strip()[:40]} .. {h1_block[-1].strip()[:20]})")

# ---- extract main showPowerSavingDialog verbatim ----
s = next(i for i,l in enumerate(MN) if l.strip().startswith("private fun showPowerSavingDialog("))
ps_block = []
depth=0
for i in range(s, len(MN)):
    depth += MN[i].count("{") - MN[i].count("}")
    ps_block.append(MN[i])
    if depth==0 and i>s:
        break
assert ps_block, "showPowerSavingDialog not found in main"
print(f"  main showPowerSavingDialog: {len(ps_block)} lines")

# ---- unified showMoreMenu ----
UNIFIED = '''    private fun showMoreMenu() = showPicker("Pulse Player \u00b7 More", arrayOf(
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

def splice(a, b, repl):  # replace 1-indexed inclusive [a,b]
    global L
    L = L[:a-1] + repl + L[b:]

# ORDER: bottom to top
# Hunk 5 (1360-1379): main 2-row nav + World button
hunk5 = (
    "        primaryTabs.addView(sourceButton, LinearLayout.LayoutParams(0, dp(48), 1f))\n"
    "        primaryTabs.addView(button(\"World \u266a\") { showWorldMenu() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })\n"
    "        primaryTabs.addView(button(\"\u266a FX\") { selectMixPreset() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })\n"
    "        tabs.addView(primaryTabs)\n"
    "        val categoryTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }\n"
    "        categoryTabs.addView(button(\"\u266a Music\") { switchPlaylist(\"DEVICE\") },\n"
    "            LinearLayout.LayoutParams(0, dp(48), 1f))\n"
    "        categoryTabs.addView(button(\"\U0001F4EB Radio\") { switchPlaylist(\"RADIO\") },\n"
    "            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(5) })\n"
    "        categoryTabs.addView(button(\"\u2605 My Radio\") { switchPlaylist(\"MY RADIO\") },\n"
    "            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(5) })\n"
    "        tabs.addView(categoryTabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })\n"
).split("\n")[:-1]
splice(1360, 1379, hunk5)

# Region 999-1257 (hunks 3+4): unified menu + #1 methods + main powerSaving
region = UNIFIED.split("\n") + [""] + h1_block + [""] + ps_block
splice(999, 1257, region)

# Hunk 2 (502-709): keep main's methods (504-708)
splice(502, 709, L[503:708])

# Hunk 1 (77-97): union of #1 fields (78-86) + main fields (88-96)
splice(77, 97, L[77:86] + L[87:96])

out = "\n".join(L) + "\n"
open(F, "w").write(out)
marks = [x for x in out.splitlines() if re.match(r"^(<<<<<<<|=======|>>>>>>>)", x)]
print(f"\nmarkers remaining: {len(marks)}")
print(f"new line count: {len(out.splitlines())}")
if marks:
    for m in marks[:10]: print("  MARK:", m)
