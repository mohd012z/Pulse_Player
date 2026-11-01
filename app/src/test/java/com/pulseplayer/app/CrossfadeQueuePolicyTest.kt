package com.pulseplayer.app

import org.junit.Assert.*
import org.junit.Test

class CrossfadeQueuePolicyTest {
    @Test fun endOfQueueAndWrap() {
        assertNull(CrossfadeQueuePolicy.nextIndex(2, 3, 0, false, null))
        assertEquals(0, CrossfadeQueuePolicy.nextIndex(2, 3, 2, false, null))
        assertEquals(1, CrossfadeQueuePolicy.nextIndex(0, 3, 0, false, null))
    }
    @Test fun repeatOneAndSingleTrackAreExcluded() {
        assertNull(CrossfadeQueuePolicy.nextIndex(0, 3, 1, false, null))
        assertNull(CrossfadeQueuePolicy.nextIndex(0, 1, 2, false, null))
    }
    @Test fun shuffleRequiresDistinctValidNextItem() {
        assertNull(CrossfadeQueuePolicy.nextIndex(0, 3, 0, true, null))
        assertNull(CrossfadeQueuePolicy.nextIndex(0, 3, 0, true, 0))
        assertNull(CrossfadeQueuePolicy.nextIndex(0, 3, 0, true, 8))
        assertEquals(2, CrossfadeQueuePolicy.nextIndex(0, 3, 0, true, 2))
    }
    @Test fun invalidQueuePositionIsRejected() {
        assertNull(CrossfadeQueuePolicy.nextIndex(-1, 3, 0, false, null))
        assertNull(CrossfadeQueuePolicy.nextIndex(3, 3, 0, false, null))
    }
}
