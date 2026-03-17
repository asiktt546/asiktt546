package com.meshchat.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runtime-статусы фонового сервиса и lock-механизмов.
 */
object BackgroundRuntimeStatus {
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _wifiLockHeld = MutableStateFlow(false)
    val wifiLockHeld: StateFlow<Boolean> = _wifiLockHeld.asStateFlow()

    private val _wakeLockHeld = MutableStateFlow(false)
    val wakeLockHeld: StateFlow<Boolean> = _wakeLockHeld.asStateFlow()

    fun updateServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun updateWifiLockHeld(held: Boolean) {
        _wifiLockHeld.value = held
    }

    fun updateWakeLockHeld(held: Boolean) {
        _wakeLockHeld.value = held
    }
}
