package com.example.perfectoutfit.testutil

import com.example.perfectoutfit.core.notification.FakeRatingReminder
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import com.example.perfectoutfit.feature.widget.FakeWidgetUpdater

/** An [OutfitLogging] over the fake DAOs, for tests that only need the replace-all path. */
fun outfitLoggingOver(
    entryDao: FakeOutfitEntryDao = FakeOutfitEntryDao(),
    itemDao: FakeOutfitItemDao = FakeOutfitItemDao(),
    snapshotDao: FakeWeatherSnapshotDao = FakeWeatherSnapshotDao(),
    reminder: FakeRatingReminder = FakeRatingReminder(),
    widget: FakeWidgetUpdater = FakeWidgetUpdater()
) = OutfitLogging(entryDao, itemDao, snapshotDao, FakeDatabaseTransactionRunner(), reminder, widget)
