package com.xnotes.ui

import androidx.compose.foundation.lazy.LazyListItemInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class PageReorderGestureTest {

    private class FakeItemInfo(
        override val index: Int,
        override val offset: Int,
        override val size: Int,
        override val key: Any = index,
        override val contentType: Any? = null,
    ) : LazyListItemInfo

    @Test
    fun calculateTargetIndexForwardMove() {
        // Moving page 0 to slot 3 in a 4-page doc lands at index 2 (between 2 and 3)
        assertEquals(2, calculateTargetIndex(fromIndex = 0, dropSlot = 3, totalItems = 4))
        // Moving page 0 to slot 4 lands at index 3 (last)
        assertEquals(3, calculateTargetIndex(fromIndex = 0, dropSlot = 4, totalItems = 4))
    }

    @Test
    fun calculateTargetIndexBackwardMove() {
        // Moving page 3 to slot 0 lands at index 0 (first)
        assertEquals(0, calculateTargetIndex(fromIndex = 3, dropSlot = 0, totalItems = 4))
        // Moving page 3 to slot 1 lands at index 1
        assertEquals(1, calculateTargetIndex(fromIndex = 3, dropSlot = 1, totalItems = 4))
    }

    @Test
    fun calculateTargetIndexAdjacentSwaps() {
        // Moving index 1 backward to slot 0 lands at index 0
        assertEquals(0, calculateTargetIndex(fromIndex = 1, dropSlot = 0, totalItems = 4))
        // Moving index 1 forward to slot 3 lands at index 2
        assertEquals(2, calculateTargetIndex(fromIndex = 1, dropSlot = 3, totalItems = 4))
    }

    @Test
    fun calculateTargetIndexNoopOnSameAndAdjacentSlot() {
        // Dropping at current slot or current slot + 1 results in no movement
        assertEquals(1, calculateTargetIndex(fromIndex = 1, dropSlot = 1, totalItems = 4))
        assertEquals(1, calculateTargetIndex(fromIndex = 1, dropSlot = 2, totalItems = 4))
    }

    @Test
    fun calculateTargetIndexBoundaryClamping() {
        // Out of bounds dropSlot clamps safely
        assertEquals(0, calculateTargetIndex(fromIndex = 2, dropSlot = -5, totalItems = 4))
        assertEquals(3, calculateTargetIndex(fromIndex = 1, dropSlot = 99, totalItems = 4))
    }

    @Test
    fun calculateTargetIndexSingleItem() {
        assertEquals(0, calculateTargetIndex(fromIndex = 0, dropSlot = 1, totalItems = 1))
        assertEquals(0, calculateTargetIndex(fromIndex = 0, dropSlot = 0, totalItems = 1))
    }

    @Test
    fun calculateDropSlotAboveFirstItem() {
        val items = listOf(
            FakeItemInfo(index = 0, offset = 0, size = 100),
            FakeItemInfo(index = 1, offset = 110, size = 100),
            FakeItemInfo(index = 2, offset = 220, size = 100),
        )
        // Finger above midpoint of item 0 (midpoint = 50) -> slot 0
        assertEquals(0, calculateDropSlot(fingerY = 20f, visibleItems = items, totalItems = 3))
        assertEquals(0, calculateDropSlot(fingerY = -10f, visibleItems = items, totalItems = 3))
    }

    @Test
    fun calculateDropSlotBetweenItems() {
        val items = listOf(
            FakeItemInfo(index = 0, offset = 0, size = 100),
            FakeItemInfo(index = 1, offset = 110, size = 100),
            FakeItemInfo(index = 2, offset = 220, size = 100),
        )
        // Midpoints: item 0 = 50, item 1 = 160, item 2 = 270
        // finger at 100 (between 50 and 160) -> slot 1
        assertEquals(1, calculateDropSlot(fingerY = 100f, visibleItems = items, totalItems = 3))
        // finger at 200 (between 160 and 270) -> slot 2
        assertEquals(2, calculateDropSlot(fingerY = 200f, visibleItems = items, totalItems = 3))
    }

    @Test
    fun calculateDropSlotBelowLastItem() {
        val items = listOf(
            FakeItemInfo(index = 0, offset = 0, size = 100),
            FakeItemInfo(index = 1, offset = 110, size = 100),
            FakeItemInfo(index = 2, offset = 220, size = 100),
        )
        // finger past midpoint of item 2 (270) -> slot 3 (after item 2)
        assertEquals(3, calculateDropSlot(fingerY = 280f, visibleItems = items, totalItems = 3))
        assertEquals(3, calculateDropSlot(fingerY = 500f, visibleItems = items, totalItems = 3))
    }

    @Test
    fun calculateDropSlotScrolledList() {
        val items = listOf(
            FakeItemInfo(index = 5, offset = -30, size = 100), // mid = 20
            FakeItemInfo(index = 6, offset = 80, size = 100),  // mid = 130
            FakeItemInfo(index = 7, offset = 190, size = 100), // mid = 240
        )
        // finger at 10f (above mid 20) -> slot 5
        assertEquals(5, calculateDropSlot(fingerY = 10f, visibleItems = items, totalItems = 10))
        // finger at 70f (between mid 20 and mid 130) -> slot 6
        assertEquals(6, calculateDropSlot(fingerY = 70f, visibleItems = items, totalItems = 10))
        // finger at 180f (between mid 130 and mid 240) -> slot 7
        assertEquals(7, calculateDropSlot(fingerY = 180f, visibleItems = items, totalItems = 10))
        // finger at 300f (below mid 240) -> slot 8
        assertEquals(8, calculateDropSlot(fingerY = 300f, visibleItems = items, totalItems = 10))
    }

    @Test
    fun calculateDropSlotEmptyOrSingleItem() {
        assertEquals(0, calculateDropSlot(fingerY = 50f, visibleItems = emptyList<LazyListItemInfo>(), totalItems = 0))
        assertEquals(0, calculateDropSlot(fingerY = 50f, visibleItems = emptyList<LazyListItemInfo>(), totalItems = -1))
        val single = listOf<LazyListItemInfo>(FakeItemInfo(index = 0, offset = 0, size = 100))
        assertEquals(0, calculateDropSlot(fingerY = 30f, visibleItems = single, totalItems = 1))
        assertEquals(1, calculateDropSlot(fingerY = 80f, visibleItems = single, totalItems = 1))
    }
}
