package com.example.perfectoutfit.feature.rate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsavedEditsTest {

    private val saved = OutfitEdits(itemIds = setOf(1L, 2L), comfortRating = 3, notes = "Windy")
    private val loaded = RateOutfitUiState(
        mode = OutfitScreenMode.RateExisting(7),
        selectedItemIds = saved.itemIds,
        comfortRating = saved.comfortRating,
        notes = saved.notes,
        savedEdits = saved
    )

    @Test
    fun `an entry as loaded has no unsaved edits`() {
        assertFalse(loaded.hasUnsavedEdits)
    }

    @Test
    fun `changing items, rating or notes is an unsaved edit`() {
        assertTrue(loaded.copy(selectedItemIds = setOf(1L)).hasUnsavedEdits)
        assertTrue(loaded.copy(comfortRating = null).hasUnsavedEdits)
        assertTrue(loaded.copy(notes = "Windy!").hasUnsavedEdits)
    }

    @Test
    fun `undoing a change leaves no unsaved edits`() {
        assertFalse(loaded.copy(selectedItemIds = setOf(2L, 1L)).hasUnsavedEdits)
    }

    @Test
    fun `new outfits are never flagged here`() {
        assertFalse(RateOutfitUiState(selectedItemIds = setOf(1L), notes = "x").hasUnsavedEdits)
    }
}
