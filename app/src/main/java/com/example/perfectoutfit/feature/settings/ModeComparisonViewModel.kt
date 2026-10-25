package com.example.perfectoutfit.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.feature.recommendation.ModeComparison
import com.example.perfectoutfit.feature.recommendation.ModeComparisonLine
import com.example.perfectoutfit.feature.recommendation.ModeVerdict
import com.example.perfectoutfit.feature.recommendation.Recommendations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ModeComparisonViewModel @Inject constructor(
    recommendations: Recommendations
) : ViewModel() {

    /** Null until the first comparison has been worked out. */
    val comparison: StateFlow<ModeComparison?> = recommendations.modeComparison()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** The verdict line shared by the Settings row and the comparison screen. */
internal fun ModeComparisonLine.verdictText(): String = when (verdict) {
    ModeVerdict.NOT_ENOUGH_DATA -> "Not enough data yet · $pairs of ${ModeComparisonLine.MIN_PAIRS} pairs"
    ModeVerdict.NO_CLEAR_DIFFERENCE -> "No clear difference · $pairs pairs"
    ModeVerdict.FEELS_LIKE_BETTER -> "Feels like fits your ratings better · $pairs pairs"
    ModeVerdict.ACTUAL_BETTER -> "Actual fits your ratings better · $pairs pairs"
}
