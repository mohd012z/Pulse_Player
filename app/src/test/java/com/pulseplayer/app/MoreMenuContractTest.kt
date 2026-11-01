package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test

class MoreMenuContractTest {
    @Test fun labelsAndActionsHaveOneToOneStableOrdering() {
        val actions = MoreMenuContract.Action.entries
        assertEquals(13, actions.size)
        assertEquals(actions.map { it.label }, MoreMenuContract.labels().toList())
        actions.forEachIndexed { index, action ->
            assertEquals(action, MoreMenuContract.actionAt(index))
        }
    }

    @Test fun youtubeAndStoryEntriesRemainDistinct() {
        assertEquals(MoreMenuContract.Action.YOUTUBE, MoreMenuContract.actionAt(2))
        assertEquals(MoreMenuContract.Action.STORY, MoreMenuContract.actionAt(12))
        assertEquals("YouTube / YT Music — download", MoreMenuContract.labels()[2])
        assertEquals("🎬 Short Drama & 📖 Novel", MoreMenuContract.labels()[12])
    }

    @Test fun invalidIndicesDoNotDispatch() {
        assertNull(MoreMenuContract.actionAt(-1))
        assertNull(MoreMenuContract.actionAt(13))
        assertNull(MoreMenuContract.actionAt(Int.MAX_VALUE))
    }
}
