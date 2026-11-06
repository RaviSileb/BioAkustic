package com.bioakustic.app.ui.usb

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bioakustic.app.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class UsbViewModel(app: android.app.Application) : ViewModel() {
    private val usbManager = app.getSystemService(Context.USB_SERVICE) as UsbManager

    data class UsbInfo(
        val name: String,
        val vendorId: Int,
        val productId: Int,
        val deviceClass: Int,
        val deviceSubclass: Int,
        val isAudio: Boolean,
    )

    private val _devices = MutableStateFlow<List<UsbInfo>>(emptyList())
    val devices: StateFlow<List<UsbInfo>> = _devices

    init {
        refresh()
    }

    fun refresh() {
        _devices.value = try {
            usbManager.deviceList.values.map { device ->
                val audioInterface = (0 until device.interfaceCount).any { i ->
                    device.getInterface(i).interfaceClass == USB_CLASS_AUDIO
                }
                UsbInfo(
                    name = device.productName ?: device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    deviceClass = device.deviceClass,
                    deviceSubclass = device.deviceSubclass,
                    isAudio = device.deviceClass == USB_CLASS_AUDIO || audioInterface,
                )
            }.sortedByDescending { it.isAudio }
        } catch (_: Exception) {
            emptyList()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UsbViewModel(container.app) as T
        }
    }

    companion object {
        const val USB_CLASS_AUDIO = 1
    }
}
