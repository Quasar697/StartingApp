package com.dji.samplev5.startingapp

import android.bluetooth.BluetoothDevice
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class BluetoothDeviceAdapter(
    private val devices: MutableList<BluetoothDeviceInfo>,
    private val onDeviceClick: (BluetoothDeviceInfo) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    private var connectedDeviceAddress: String? = null

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceIcon: ImageView = itemView.findViewById(R.id.iv_device_icon)
        val deviceName: TextView = itemView.findViewById(R.id.tv_device_name)
        val deviceAddress: TextView = itemView.findViewById(R.id.tv_device_address)
        val deviceRssi: TextView = itemView.findViewById(R.id.tv_device_rssi)
        val signalStrength: View = itemView.findViewById(R.id.view_signal_strength)
        val deviceStatus: TextView = itemView.findViewById(R.id.tv_device_status)
        val deviceType: TextView = itemView.findViewById(R.id.tv_device_type)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val deviceInfo = devices[position]

        // Nome del dispositivo con informazioni di debug
        val displayName = if (deviceInfo.deviceName.contains("sconosciuto")) {
            // Se il nome contiene "sconosciuto", mostra anche l'indirizzo per distinguere
            "Dispositivo ${deviceInfo.deviceAddress.takeLast(5)}"
        } else {
            deviceInfo.deviceName
        }
        holder.deviceName.text = displayName

        // Indirizzo MAC
        holder.deviceAddress.text = deviceInfo.deviceAddress

        // RSSI (intensità segnale)
        holder.deviceRssi.text = "${deviceInfo.rssi} dBm"

        // Status del dispositivo (connesso, accoppiato, nuovo)
        try {
            if (deviceInfo.deviceAddress == connectedDeviceAddress) {
                holder.deviceStatus.text = "🔗 Connesso"
                holder.deviceStatus.setTextColor(Color.parseColor("#4CAF50"))
                holder.deviceStatus.visibility = View.VISIBLE
                // Evidenzia il dispositivo connesso
                holder.itemView.setBackgroundColor(Color.parseColor("#E8F5E8"))
            } else if (deviceInfo.context != null && deviceInfo.hasBluetoothConnectPermission(deviceInfo.context)) {
                // Ripristina sfondo normale
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)

                when (deviceInfo.device.bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        holder.deviceStatus.text = "Accoppiato"
                        holder.deviceStatus.setTextColor(Color.parseColor("#FF9800"))
                        holder.deviceStatus.visibility = View.VISIBLE
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        holder.deviceStatus.text = "In accoppiamento..."
                        holder.deviceStatus.setTextColor(Color.parseColor("#FF9800"))
                        holder.deviceStatus.visibility = View.VISIBLE
                    }
                    else -> {
                        holder.deviceStatus.text = "Tocca per connettere"
                        holder.deviceStatus.setTextColor(Color.parseColor("#2196F3"))
                        holder.deviceStatus.visibility = View.VISIBLE
                    }
                }
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
                holder.deviceStatus.visibility = View.GONE
            }
        } catch (e: SecurityException) {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            holder.deviceStatus.visibility = View.GONE
        }

        // Tipo di dispositivo abbreviato (nascondi se sconosciuto)
        val deviceTypeShort = when {
            deviceInfo.getDeviceType().contains("Telefono") -> "📱"
            deviceInfo.getDeviceType().contains("Audio") -> "🎧"
            deviceInfo.getDeviceType().contains("Computer") -> "💻"
            deviceInfo.getDeviceType().contains("Indossabile") -> "⌚"
            else -> ""  // Stringa vuota invece del punto interrogativo
        }

        if (deviceTypeShort.isNotEmpty()) {
            holder.deviceType.text = deviceTypeShort
            holder.deviceType.visibility = View.VISIBLE
        } else {
            holder.deviceType.visibility = View.GONE
        }

        // Colore dell'indicatore di segnale
        try {
            holder.signalStrength.setBackgroundColor(Color.parseColor(deviceInfo.getSignalColor()))
        } catch (e: Exception) {
            holder.signalStrength.setBackgroundColor(Color.GRAY)
        }

        // Larghezza dell'indicatore basata sulla forza del segnale
        val layoutParams = holder.signalStrength.layoutParams
        layoutParams.width = (deviceInfo.getSignalStrengthPercentage() * 0.4).toInt() + 20
        holder.signalStrength.layoutParams = layoutParams

        // Icona basata sul tipo di dispositivo
        val iconResource = when {
            deviceInfo.getDeviceType().contains("Telefono") -> android.R.drawable.stat_sys_phone_call
            deviceInfo.getDeviceType().contains("Audio") -> android.R.drawable.ic_media_play
            deviceInfo.getDeviceType().contains("Computer") -> android.R.drawable.stat_sys_download
            else -> android.R.drawable.stat_sys_data_bluetooth
        }
        holder.deviceIcon.setImageResource(iconResource)

        // Click listener normale
        holder.itemView.setOnClickListener {
            onDeviceClick(deviceInfo)
        }

        // Long click per mostrare informazioni di debug
        holder.itemView.setOnLongClickListener {
            showDebugInfo(it, deviceInfo)
            true
        }
    }

    private fun showDebugInfo(view: View, deviceInfo: BluetoothDeviceInfo) {
        val isConnected = deviceInfo.deviceAddress == connectedDeviceAddress

        if (isConnected) {
            // Se connesso, offri opzioni aggiuntive
            val options = arrayOf("Mostra info debug", "Invia dati di test", "Disconnetti")

            android.app.AlertDialog.Builder(view.context)
                .setTitle("Opzioni dispositivo connesso")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            val debugInfo = deviceInfo.getDebugInfo()
                            Toast.makeText(view.context, debugInfo, Toast.LENGTH_LONG).show()
                            android.util.Log.i("BluetoothDebug", debugInfo)
                        }
                        1 -> {
                            // Nota: questa chiamata dovrebbe essere gestita dalla MainActivity
                            // Per ora mostra solo un messaggio
                            Toast.makeText(view.context, "Usa il click normale per opzioni connessione", Toast.LENGTH_LONG).show()
                        }
                        2 -> {
                            Toast.makeText(view.context, "Usa il click normale per disconnettere", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
        } else {
            // Se non connesso, mostra solo info debug
            val debugInfo = deviceInfo.getDebugInfo()
            Toast.makeText(view.context, debugInfo, Toast.LENGTH_LONG).show()
            android.util.Log.i("BluetoothDebug", debugInfo)
        }
    }

    override fun getItemCount(): Int = devices.size

    /**
     * Aggiunge un nuovo dispositivo alla lista (evita duplicati)
     */
    fun addDevice(deviceInfo: BluetoothDeviceInfo) {
        val existingIndex = devices.indexOfFirst { it.deviceAddress == deviceInfo.deviceAddress }
        if (existingIndex != -1) {
            // Aggiorna il dispositivo esistente (potrebbe avere RSSI diverso)
            devices[existingIndex] = deviceInfo
            notifyItemChanged(existingIndex)

            // Log per debug
            android.util.Log.d("BluetoothAdapter", "Dispositivo aggiornato: ${deviceInfo.deviceAddress} - Nome: '${deviceInfo.deviceName}'")
        } else {
            // Aggiungi nuovo dispositivo
            devices.add(deviceInfo)
            notifyItemInserted(devices.size - 1)

            // Log per debug
            android.util.Log.d("BluetoothAdapter", "Nuovo dispositivo: ${deviceInfo.deviceAddress} - Nome: '${deviceInfo.deviceName}'")
        }
    }

    /**
     * Pulisce la lista dei dispositivi
     */
    fun clearDevices() {
        val size = devices.size
        devices.clear()
        notifyItemRangeRemoved(0, size)
        android.util.Log.d("BluetoothAdapter", "Lista dispositivi pulita")
    }

    /**
     * Aggiorna lo stato di connessione
     */
    fun updateConnectionStatus(connectedAddress: String?) {
        connectedDeviceAddress = connectedAddress
        android.util.Log.d("BluetoothAdapter", "Stato connessione aggiornato: $connectedAddress")
    }

    /**
     * Restituisce tutti i dispositivi nella lista
     */
    fun getAllDevices(): List<BluetoothDeviceInfo> = devices.toList()
}