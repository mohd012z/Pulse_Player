import com.pulseplayer.app.MiniPlayerGestures

fun main() {
    val g = MiniPlayerGestures
    check(g.classify(-80f, 4f, -330f, 55f) == MiniPlayerGestures.Action.NEXT_TRACK)
    check(g.classify(90f, 3f, 400f, 55f) == MiniPlayerGestures.Action.PREVIOUS_TRACK)
    check(g.classify(18f, 2f, 400f, 55f) == MiniPlayerGestures.Action.IGNORE)
    check(g.classify(90f, 90f, 400f, 55f) == MiniPlayerGestures.Action.IGNORE)
    check(g.classify(-90f, 2f, 100f, 55f) == MiniPlayerGestures.Action.IGNORE)
    check(g.classify(-90f, 2f, 400f, 55f) == MiniPlayerGestures.Action.IGNORE)
    check(g.classify(Float.NaN, 2f, 400f, 55f) == MiniPlayerGestures.Action.IGNORE)
    check(g.classify(-90f, 2f, -400f, 0f) == MiniPlayerGestures.Action.IGNORE)
    println("MiniPlayerGestures: 8 assertions PASSED")
}