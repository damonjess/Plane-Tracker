package com.example.plane_tracker.ui

/**
 * Backwards-compatible entry points. The live implementation lives in
 * MainActivity.TrackerScreen; these wrappers keep old call sites compiling.
 */
@androidx.compose.runtime.Composable
fun PlaneTrackerScreen() {
    com.example.plane_tracker.TrackerScreen()
}

@androidx.compose.runtime.Composable
fun MapScreen() {
    PlaneTrackerScreen()
}
