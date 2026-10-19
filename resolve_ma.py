import sys
F = "app/src/main/java/com/pulseplayer/app/MainActivity.kt"
L = open(F).read().splitlines(keepends=False)

def assert_line(n, frag):
    if frag not in L[n-1]:
        raise SystemExit(f"LINE MISMATCH @{n}: expected '{frag}' in '{L[n-1][:60]}'")

# sanity-verify every marker line before splicing
assert_line(77, "<<<<<<< HEAD"); assert_line(87, "======="); assert_line(97, ">>>>>>> origin/main")
assert_line(502, "<<<<<<< HEAD"); assert_line(503, "======="); assert_line(709, ">>>>>>> origin/main")
assert_line(1000, "<<<<<<< HEAD"); assert_line(1004, "======="); assert_line(1007, ">>>>>>> origin/main")
assert_line(1053, "<<<<<<< HEAD"); assert_line(1231, "======="); assert_line(1255, ">>>>>>> origin/main")
assert_line(1360, "<<<<<<< HEAD"); assert_line(1366, "======="); assert_line(1379, ">>>>>>> origin/main")

authored_when = (
    "            6 -> shareCurrentTrack()\n"
    "            7 -> showPicker(\"Lyrics / subtitles\", arrayOf(\"Synchronized .lrc (local import)\", \"Timed subtitles for current track\")) { ly ->\n"
    "                if (ly == 0) showLyricsDialog() else lyricsForCurrent()\n"
    "            }\n"
    "            8 -> showWorldMenu()\n"
    "            9 -> showPicker(\"Choose in-app provider\", arrayOf(\"YouTube\", \"YouTube Music\", \"Spotify Embed\", \"JOOX Website\")) { which ->\n"
    "                val provider = listOf(\n"
    "                    ProviderMediaUrls.Provider.YOUTUBE,\n"
    "                    ProviderMediaUrls.Provider.YOUTUBE_MUSIC,\n"
    "                    ProviderMediaUrls.Provider.SPOTIFY,\n"
    "                    ProviderMediaUrls.Provider.JOOX\n"
    "                )[which]\n"
    "                openInAppProvider(provider)\n"
    "            }\n"
    "            10 -> requestSpectrum()\n"
    "            11 -> showPowerSavingDialog()\n"
    "            12 -> Phase1InspectorDialog.show(this)\n"
    "        }\n"
    "    }\n"
)

hunk3_array = (
    "        \"Import / file manager\", \"Online music services\", \"Malaysian radio\", \"Audio presets / bass\",\n"
    "        \"Playback preferences\", \"Bluetooth / Wi-Fi / output\", \"Share current song\", \"Lyrics / subtitles\",\n"
    "        \"World music catalog (Jamendo)\", \"In-app YouTube / Spotify / JOOX\", \"Live audio spectrum\",\n"
    "        \"Power saving mode\", \"Inspect media URL\"\n"
)

hunk5_nav = (
    "        primaryTabs.addView(sourceButton, LinearLayout.LayoutParams(0, dp(48), 1f))\n"
    "        primaryTabs.addView(button(\"World \u266a\") { showWorldMenu() },\n"
    "            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })\n"
    "        primaryTabs.addView(button(\"\u266a FX\") { selectMixPreset() },\n"
    "            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })\n"
    "        tabs.addView(primaryTabs)\n"
    "        val categoryTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }\n"
    "        categoryTabs.addView(button(\"\u266a Music\") { switchPlaylist(\"DEVICE\") },\n"
    "            LinearLayout.LayoutParams(0, dp(48), 1f))\n"
    "        categoryTabs.addView(button(\"\U0001F4EB Radio\") { switchPlaylist(\"RADIO\") },\n"
    "            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(5) })\n"
    "        categoryTabs.addView(button(\"\u2605 My Radio\") { switchPlaylist(\"MY RADIO\") },\n"
    "            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(5) })\n"
    "        tabs.addView(categoryTabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })\n"
)

def splice(repl_lines, a, b):
    """replace 1-indexed inclusive [a,b] with repl_lines"""
    global L
    L = L[:a-1] + repl_lines + L[b:]

# --- Hunk 4 (1053-1255): authored when-cases + #1 methods + main showPowerSavingDialog ---
hunk4 = (authored_when.rstrip("\n").split("\n")
         + L[1068:1230]          # #1 methods: clearPickedFiles..showLyricsDialog (1069-1230)
         + [""]
         + L[1237:1254])         # main showPowerSavingDialog body (1238-1254), shared tail 1256-1257 closes it
splice(hunk4, 1053, 1255)

# --- Hunk 3 (1000-1007): unified 13-item menu array ---
splice(hunk3_array.split("\n"), 1000, 1007)

# --- Hunk 2 (502-709): keep main's added methods (504-708); HEAD side was empty ---
splice(L[503:708], 502, 709)

# --- Hunk 1 (77-97): union of #1 fields (78-86) + main fields (88-96) ---
splice(L[77:86] + L[87:96], 77, 97)

out = "\n".join(L) + "\n"
open(F, "w").write(out)

# verify no markers remain
import re
marks = [ln for ln in out.splitlines() if re.match(r"^(<<<<<<<|=======|>>>>>>>)", ln)]
print(f"markers remaining: {len(marks)}")
print(f"new line count: {len(out.splitlines())}")
if marks:
    for m in marks[:10]: print("  MARK:", m)
