import com.pulseplayer.app.SoundPresets

fun main() {
    check(SoundPresets.names.size == 21)
    check(SoundPresets.gainDb("Flat", 20.0) == 0.0)
    check(SoundPresets.gainDb("Flat", 20000.0) == 0.0)
    check(SoundPresets.gainDb("Warm", 100.0) > SoundPresets.gainDb("Warm", 8000.0))
    check(SoundPresets.gainDb("Vocal", 1500.0) > SoundPresets.gainDb("Vocal", 70.0))
    check(SoundPresets.gainDb("Bright", 10000.0) > SoundPresets.gainDb("Bright", 150.0))
    check(runCatching { SoundPresets.gainDb("Unknown", 100.0) }.isFailure)
    check(runCatching { SoundPresets.gainDb("Flat", -1.0) }.isFailure)
    check(runCatching { SoundPresets.gainDb("Flat", Double.NaN) }.isFailure)
    check(SoundPresets.gainDb("Studio Punch", 90.0) > SoundPresets.gainDb("Studio Punch", 1000.0))
    check(SoundPresets.gainDb("Cinema", 100.0) > SoundPresets.gainDb("Cinema", 900.0))
    check(SoundPresets.gainDb("Night", 100.0) < SoundPresets.gainDb("Night", 1000.0))
    check(SoundPresets.gainDb("Airy", 10000.0) > SoundPresets.gainDb("Airy", 100.0))
    check(SoundPresets.gainDb("Immersive", 12000.0) > SoundPresets.gainDb("Immersive", 400.0))
    check(SoundPresets.gainDb("NovaStream TV", 2400.0) > SoundPresets.gainDb("NovaStream TV", 550.0))
    check(SoundPresets.names.distinct().size == SoundPresets.names.size)
    println("SoundPresets: 16 assertions PASSED")
}