package com.example.plane_tracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.plane_tracker.data.FlightRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FlightViewModel : ViewModel() {
    private val repository = FlightRepository()

    // StateFlow securely holds the latest data so the UI can observe it
    private val _geoJsonData = MutableStateFlow("""{"type":"FeatureCollection","features":[]}""")
    val geoJsonData: StateFlow<String> = _geoJsonData.asStateFlow()

    init {
        startTracking()
    }

    private fun startTracking() {
        // viewModelScope automatically cancels the loop if the app is closed
        viewModelScope.launch {
            while (true) {
                val newData = repository.fetchGeoJsonFlights()
                if (newData.isNotEmpty()) {
                    _geoJsonData.value = newData
                }
                delay(10000)
            }
        }
    }
}
