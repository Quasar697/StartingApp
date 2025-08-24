package com.dji.samplev5.startingapp

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.util.Log

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
     * Ottiene il nome del dispositivo gestendo i permessi con debug avanzato
     */
    private fun getDeviceNameSafely(): String {
        return try {
            Log.d("BluetoothDebug", "Tentativo di ottenere il nome per dispositivo: ${device.address}")

            if (context == null) {
                Log.w("BluetoothDebug", "Context è null - impossibile verificare permessi")
                return "Dispositivo sconosciuto (no context)"
            }

            // Verifica permessi
            val hasBluetoothConnect = hasBluetoothConnectPermission(context)
            Log.d("BluetoothDebug", "Permesso BLUETOOTH_CONNECT: $hasBluetoothConnect")

            if (!hasBluetoothConnect) {
                Log.w("BluetoothDebug", "Permesso BLUETOOTH_CONNECT mancante")
                return "Dispositivo sconosciuto (no permission)"
            }

            // Tenta di ottenere il nome
            val deviceName = device.name
            Log.d("BluetoothDebug", "Nome ottenuto dal dispositivo: '$deviceName'")

            return when {
                deviceName.isNullOrBlank() -> {
                    Log.i("BluetoothDebug", "Nome dispositivo è null/vuoto - probabilmente non trasmette il nome")
                    "Dispositivo sconosciuto (no name)"
                }
                else -> {
                    Log.i("BluetoothDebug", "Nome dispositivo trovato: $deviceName")
                    deviceName
                }
            }
        } catch (e: SecurityException) {
            Log.e("BluetoothDebug", "SecurityException nell'ottenere il nome: ${e.message}")
            "Dispositivo sconosciuto (security error)"
        } catch (e: Exception) {
            Log.e("BluetoothDebug", "Errore generico nell'ottenere il nome: ${e.message}")
            "Dispositivo sconosciuto (error)"
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
     * Restituisce il tipo di dispositivo basato sulla classe con debug
     */
    fun getDeviceType(): String {
        return try {
            if (context != null && hasBluetoothConnectPermission(context)) {
                val bluetoothClass = device.bluetoothClass
                Log.d("BluetoothDebug", "BluetoothClass: ${bluetoothClass?.majorDeviceClass}")

                when (bluetoothClass?.majorDeviceClass) {
                    0x0100 -> "Computer"
                    0x0200 -> "Telefono"
                    0x0400 -> "Audio"
                    0x0500 -> "Periferica"
                    0x0600 -> "Imaging"
                    0x0700 -> "Indossabile"
                    0x0800 -> "Giocattolo"
                    0x0900 -> "Salute"
                    else -> "Sconosciuto (${bluetoothClass?.majorDeviceClass})"
                }
            } else {
                "Sconosciuto (no permission)"
            }
        } catch (e: SecurityException) {
            Log.e("BluetoothDebug", "SecurityException nel getDeviceType: ${e.message}")
            "Sconosciuto (security error)"
        }
    }

    /**
     * Verifica se abbiamo il permesso BLUETOOTH_CONNECT con logging (pubblico per adapter)
     */
    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val hasPermission = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            Log.d("BluetoothDebug", "Android 12+: BLUETOOTH_CONNECT permission = $hasPermission")
            hasPermission
        } else {
            Log.d("BluetoothDebug", "Android < 12: permessi legacy OK")
            true // Per versioni precedenti ad Android 12, i permessi nel manifest sono sufficienti
        }
    }

    /**
     * Funzione di debug per ottenere tutte le informazioni disponibili
     */
    fun getDebugInfo(): String {
        val sb = StringBuilder()
        sb.append("=== DEBUG INFO ===\n")
        sb.append("Address: $deviceAddress\n")
        sb.append("RSSI: $rssi dBm\n")
        sb.append("Android Version: ${android.os.Build.VERSION.SDK_INT}\n")
        sb.append("Context available: ${context != null}\n")

        if (context != null) {
            sb.append("BLUETOOTH_CONNECT permission: ${hasBluetoothConnectPermission(context)}\n")
        }

        try {
            if (context != null && hasBluetoothConnectPermission(context)) {
                sb.append("Raw device name: '${device.name}'\n")
                sb.append("Device type: ${device.type}\n")
                sb.append("Bond state: ${device.bondState}\n")
                sb.append("Bluetooth class: ${device.bluetoothClass}\n")
            } else {
                sb.append("Cannot access device details - no permission\n")
            }
        } catch (e: SecurityException) {
            sb.append("SecurityException: ${e.message}\n")
        }

        return sb.toString()
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