package com.dji.samplev5.startingapp

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

data class BluetoothDeviceInfo(
    val device: BluetoothDevice,
    val rssi: Int = 0,
    val context: Context? = null
) {

    val deviceName: String
        get() = getDeviceNameSafely()

    val deviceAddress: String
        get() = device.address ?: "Indirizzo sconosciuto"

    /**
     * Ottiene il nome del dispositivo gestendo i permessi
     */
    private fun getDeviceNameSafely(): String {
        return try {
            if (context != null && hasBluetoothConnectPermission(context)) {
                device.name ?: "Dispositivo sconosciuto"
            } else {
                "Dispositivo sconosciuto"
            }
        } catch (e: SecurityException) {
            "Dispositivo sconosciuto"
        }
    }

    /**
     * Restituisce l'intensità del segnale come percentuale (0-100)
     */
    fun getSignalStrengthPercentage(): Int {
        // RSSI tipicamente va da -100 (molto debole) a -30 (molto forte)
        return when {
            rssi >= -50 -> 100  // Segnale molto forte
            rssi >= -60 -> 80   // Segnale forte
            rssi >= -70 -> 60   // Segnale buono
            rssi >= -80 -> 40   // Segnale debole
            rssi >= -90 -> 20   // Segnale molto debole
            else -> 10          // Segnale quasi inesistente
        }
    }

    /**
     * Restituisce il colore per l'indicatore di segnale
     */
    fun getSignalColor(): String {
        return when {
            rssi >= -60 -> "#4CAF50"  // Verde - Buono
            rssi >= -75 -> "#FF9800"  // Arancione - Medio
            else -> "#F44336"         // Rosso - Debole
        }
    }

    /**
     * Restituisce il tipo di dispositivo basato sulla classe
     */
    fun getDeviceType(): String {
        return try {
            if (context != null && hasBluetoothConnectPermission(context)) {
                when (device.bluetoothClass?.majorDeviceClass) {
                    0x0100 -> "Computer"
                    0x0200 -> "Telefono"
                    0x0400 -> "Audio"
                    0x0500 -> "Periferica"
                    0x0600 -> "Imaging"
                    0x0700 -> "Indossabile"
                    0x0800 -> "Giocattolo"
                    0x0900 -> "Salute"
                    else -> "Sconosciuto"
                }
            } else {
                "Sconosciuto"
            }
        } catch (e: SecurityException) {
            "Sconosciuto"
        }
    }

    /**
     * Verifica se abbiamo il permesso BLUETOOTH_CONNECT
     */
    private fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Per versioni precedenti ad Android 12, i permessi nel manifest sono sufficienti
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BluetoothDeviceInfo) return false
        return deviceAddress == other.deviceAddress
    }

    override fun hashCode(): Int {
        return deviceAddress.hashCode()
    }
}