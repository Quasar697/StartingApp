package com.dji.samplev5.startingapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * Gestisce le connessioni Bluetooth con i dispositivi
 */
class BluetoothConnectionManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val connectionListener: ConnectionListener
) {

    companion object {
        private const val TAG = "BluetoothConnection"
        // UUID standard per connessioni seriali Bluetooth (SPP - Serial Port Profile)
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    interface ConnectionListener {
        fun onConnectionStarted(device: BluetoothDevice)
        fun onConnectionSuccess(device: BluetoothDevice)
        fun onConnectionFailed(device: BluetoothDevice, error: String)
        fun onDisconnected(device: BluetoothDevice)
        fun onDataReceived(device: BluetoothDevice, data: String)
    }

    private var connectedThread: ConnectedThread? = null
    private var connectThread: ConnectThread? = null

    /**
     * Avvia la connessione a un dispositivo
     */
    fun connectToDevice(deviceInfo: BluetoothDeviceInfo) {
        Log.d(TAG, "Tentativo di connessione a: ${deviceInfo.deviceAddress}")

        if (!hasBluetoothPermission()) {
            connectionListener.onConnectionFailed(
                deviceInfo.device,
                "Permessi Bluetooth mancanti"
            )
            return
        }

        // Cancella connessioni precedenti
        disconnect()

        // Ferma la discovery per migliorare le performance di connessione
        try {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Impossibile fermare la discovery: ${e.message}")
        }

        // Avvia il thread di connessione
        connectThread = ConnectThread(deviceInfo.device)
        connectThread?.start()
    }

    /**
     * Disconnette dal dispositivo corrente
     */
    fun disconnect() {
        Log.d(TAG, "Disconnessione...")

        connectThread?.cancel()
        connectThread = null

        connectedThread?.cancel()
        connectedThread = null
    }

    /**
     * Invia dati al dispositivo connesso
     */
    fun sendData(data: String): Boolean {
        return connectedThread?.write(data.toByteArray()) ?: false
    }

    /**
     * Verifica se siamo connessi
     */
    fun isConnected(): Boolean {
        return connectedThread?.isConnected() ?: false
    }

    /**
     * Thread per gestire la connessione
     */
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            try {
                if (hasBluetoothPermission()) {
                    device.createRfcommSocketToServiceRecord(SPP_UUID)
                } else {
                    null
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException nella creazione socket: ${e.message}")
                null
            } catch (e: IOException) {
                Log.e(TAG, "Errore nella creazione socket: ${e.message}")
                null
            }
        }

        override fun run() {
            Log.d(TAG, "Avvio thread di connessione")
            connectionListener.onConnectionStarted(device)

            socket?.let { bluetoothSocket ->
                try {
                    // Tentativo di connessione (operazione bloccante)
                    bluetoothSocket.connect()

                    Log.d(TAG, "Connessione riuscita!")
                    connectionListener.onConnectionSuccess(device)

                    // Avvia il thread per gestire i dati
                    connectedThread = ConnectedThread(bluetoothSocket, device)
                    connectedThread?.start()

                } catch (e: IOException) {
                    Log.e(TAG, "Connessione fallita: ${e.message}")
                    connectionListener.onConnectionFailed(device, e.message ?: "Errore sconosciuto")

                    try {
                        bluetoothSocket.close()
                    } catch (closeException: IOException) {
                        Log.e(TAG, "Errore nella chiusura socket: ${closeException.message}")
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException nella connessione: ${e.message}")
                    connectionListener.onConnectionFailed(device, "Permessi insufficienti")
                }
            } ?: run {
                connectionListener.onConnectionFailed(device, "Impossibile creare socket")
            }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Errore nella chiusura socket di connessione: ${e.message}")
            }
        }
    }

    /**
     * Thread per gestire i dati in una connessione stabilita
     */
    private inner class ConnectedThread(
        private val socket: BluetoothSocket,
        private val device: BluetoothDevice
    ) : Thread() {
        private val inputStream: InputStream? = socket.inputStream
        private val outputStream: OutputStream? = socket.outputStream
        private val buffer = ByteArray(1024)
        private var isRunning = true

        override fun run() {
            Log.d(TAG, "Thread connesso avviato")

            while (isRunning && socket.isConnected) {
                try {
                    inputStream?.let { input ->
                        val bytes = input.read(buffer)
                        if (bytes > 0) {
                            val receivedData = String(buffer, 0, bytes)
                            Log.d(TAG, "Dati ricevuti: $receivedData")
                            connectionListener.onDataReceived(device, receivedData)
                        }
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "Connessione interrotta: ${e.message}")
                    connectionListener.onDisconnected(device)
                    break
                }
            }
        }

        fun write(bytes: ByteArray): Boolean {
            return try {
                outputStream?.write(bytes)
                outputStream?.flush()
                Log.d(TAG, "Dati inviati: ${String(bytes)}")
                true
            } catch (e: IOException) {
                Log.e(TAG, "Errore nell'invio dati: ${e.message}")
                false
            }
        }

        fun isConnected(): Boolean {
            return isRunning && socket.isConnected
        }

        fun cancel() {
            isRunning = false
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Errore nella chiusura socket connesso: ${e.message}")
            }
        }
    }

    /**
     * Verifica i permessi Bluetooth
     */
    private fun hasBluetoothPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}