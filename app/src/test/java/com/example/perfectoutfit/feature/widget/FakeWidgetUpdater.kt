package com.example.perfectoutfit.feature.widget

/** Recording fake for [WidgetUpdater]; counts redraw requests. */
class FakeWidgetUpdater : WidgetUpdater {
    var redraws = 0

    override fun redraw() {
        redraws++
    }
}
