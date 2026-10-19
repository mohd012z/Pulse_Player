import re
F = "/opt/data/cache/scratch/pulse/app/src/main/java/com/pulseplayer/app/MainActivity.kt"
L = open(F, encoding="utf-8").read().splitlines()

# ---- hunk 6: decide nav. Check what the merged tree references for the adaptive-nav vars ----
for var in ["categoryTabs", "primaryTabs", "tabs", "discoverMalaysiaRadio"]:
    refs = [i+1 for i,l in enumerate(L) if re.search(rf"\b{var}\b", l)]
    print(f"{var}: {len(refs)} refs at {refs[:12]}")

# ---- hunk 3: showSpectrum current conflicted extent ----
i3 = next(i for i,l in enumerate(L) if l.strip().startswith("private fun showSpectrum"))
print(f"\nshowSpectrum starts line {i3+1}")
for i in range(i3, i3+35):
    print(f"{i+1:5}| {L[i]}")
