import re
F = "/opt/data/cache/scratch/pulse/app/src/main/java/com/pulseplayer/app/MainActivity.kt"
L = open(F, encoding="utf-8").read().splitlines()

def check(n, frag):
    if frag not in L[n-1]:
        raise SystemExit(f"MISMATCH @{n}: want '{frag[:40]}' got '{L[n-1][:40]}'")

# verify all 7 hunks at expected lines
for n,fr in [(83,"<<<<<<< HEAD"),(88,"======="),(90,">>>>>>> origin/main"),
             (134,"<<<<<<< HEAD"),(139,"======="),(141,">>>>>>> origin/main"),
             (683,"<<<<<<< HEAD"),(687,"======="),(699,">>>>>>> origin/main"),
             (1123,"<<<<<<< HEAD"),(1125,"======="),(1127,">>>>>>> origin/main"),
             (1167,"<<<<<<< HEAD"),(1171,"======="),(1174,">>>>>>> origin/main"),
             (1297,"<<<<<<< HEAD"),(1300,"======="),(1320,">>>>>>> origin/main"),
             (1538,"<<<<<<< HEAD"),(1541,"======="),(1543,">>>>>>> origin/main")]:
    check(n, fr)

def splice(a, b, repl):
    global L
    L = L[:a-1] + repl + L[b:]

# Hunk 7 (1538-1543): #2 design text size (HEAD)
splice(1538, 1543, ["        textSize = 11f",
                    "        typeface = android.graphics.Typeface.create(\"sans-serif-medium\", android.graphics.Typeface.NORMAL)"])
# Hunk 6 (1297-1320): #2 redesign nav (HEAD); main's adaptive 2-row is the #18 design, kept in #1
splice(1297, 1320, ["        sourceButton = button(\"DEVICE \u25be\") { showPlaylistDialog() }",
                    "        root.addView(sourceButton, LinearLayout.LayoutParams(-1, dp(38)).apply { bottomMargin = dp(3) })"])
# Hunk 5 (1167-1174): keep-both menu cases
splice(1167, 1174, ["            9 -> startActivity(Intent(this, MediaExportActivity::class.java))",
                    "            10 -> startActivity(Intent(this, AuthorizedDownloadActivity::class.java))",
                    "            11 -> showPerformanceSettings()",
                    "            12 -> showPowerSavingDialog()",
                    "            13 -> Phase1InspectorDialog.show(this)"])
# Hunk 4 (1123-1127): keep-both menu array
splice(1123, 1127, ["        \"Lyrics / subtitles\", \"Live audio spectrum\", \"Playback preferences\", \"Bluetooth / Wi-Fi / output\", \"Share current song\",",
                    "        \"Download & Convert (local video)\", \"Authorized direct download\", \"Device performance profile\",",
                    "        \"Power saving mode\", \"Inspect media URL\""])
# Hunk 3 (683-699): keep #2's showSpectrum design (HEAD)
splice(683, 699, ["        val row = waveformDock ?: return",
                  "        if (row.visibility == android.view.View.VISIBLE) {",
                  "            spectrumRetryAtMs = android.os.SystemClock.elapsedRealtime() + 60_000L"])
# Hunk 2 (134-141): keep-both refresh (auto-start + network speed + power-aware interval)
splice(134, 141, ["            if (controller?.isPlaying == true && spectrumView == null && !spectrumRequestPending &&",
                  "                android.os.SystemClock.elapsedRealtime() >= spectrumRetryAtMs) startSpectrumIfPermitted()",
                  "            refreshNetworkSpeed()",
                  "            clock.postDelayed(this, powerSaving.refreshIntervalMillis(controller?.isPlaying == true))"])
# Hunk 1 (83-90): union fields
splice(83, 90, ["    private var waveformDock: LinearLayout? = null",
                "    private var activeSpectrumSession = -1",
                "    private var spectrumRequestPending = false",
                "    private var spectrumRetryAtMs = 0L",
                "    private val powerSaving by lazy { PowerSavingManager(this) }"])

out = "\n".join(L) + "\n"
open(F, "w", encoding="utf-8").write(out)
marks = [x for x in out.splitlines() if re.match(r"^(<<<<<<<|=======|>>>>>>>)", x)]
print(f"markers remaining: {len(marks)}")
print(f"new line count: {len(out.splitlines())}")
# structural checks
s = out
print("brace net:", s.count("{")-s.count("}"), " paren net:", s.count("(")-s.count(")"))
dups = [c for c in __import__("collections").Counter(re.findall(r"private fun ([a-zA-Z0-9_]+)", s)).items() if c[1]>1]
print("dup fun defs:", dups or "none")
print("showMoreMenu count:", s.count("private fun showMoreMenu"))
print("showSpectrum count:", s.count("private fun showSpectrum"))
