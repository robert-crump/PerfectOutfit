package com.example.perfectoutfit.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.example.perfectoutfit.core.model.ComfortRating
import org.junit.Test

class RatingActionValidatorTest {

    @Test
    fun `valid ratings are accepted`() {
        assertEquals(RatingActionValidator.RatingRequest(1L, ComfortRating.TOO_COLD), RatingActionValidator.validate(1L, -1))
        assertEquals(RatingActionValidator.RatingRequest(1L, ComfortRating.PERFECT), RatingActionValidator.validate(1L, 0))
        assertEquals(RatingActionValidator.RatingRequest(1L, ComfortRating.TOO_HOT), RatingActionValidator.validate(1L, 1))
    }

    @Test
    fun `rating outside -1,0,1 is rejected`() {
        assertNull(RatingActionValidator.validate(1L, 2))
        assertNull(RatingActionValidator.validate(1L, Int.MIN_VALUE))
    }

    @Test
    fun `non-positive entry id is rejected`() {
        assertNull(RatingActionValidator.validate(0L, 0))
        assertNull(RatingActionValidator.validate(-1L, 0))
    }
}
