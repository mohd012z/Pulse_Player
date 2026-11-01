package com.pulseplayer.app

/** Fail closed unless the picker actually granted read access. */
internal object StoryUriGrantPolicy {
    fun persistReadGrant(grantedFlags: Int, readFlag: Int, persist: (Int) -> Unit): Boolean {
        if (readFlag == 0 || (grantedFlags and readFlag) != readFlag) return false
        return try {
            persist(readFlag)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
