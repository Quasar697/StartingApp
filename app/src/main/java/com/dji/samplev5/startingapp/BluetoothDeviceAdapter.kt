package com.dji.samplev5.startingapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BluetoothDeviceAdapter(
    private val devices: MutableList<BluetoothDeviceInfo>,
    private val onDeviceClick: (BluetoothDeviceInfo) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceIcon: ImageView = itemView.findViewById(R.id.iv_device_icon)
        val deviceName: TextView = itemView.findViewById(R.id.tv_device_name)
        val deviceAddress: TextView = itemView.findViewById(R.id.tv_device_address)
        val deviceRssi: TextView = itemView.findViewById(R.id.tv_device_rssi)
        val signalStrength: View = itemView.findViewById(R.id.view_signal_strength)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val deviceInfo = devices[position]

        // Nome del dispositivo
        holder.deviceName.text = if (deviceInfo.deviceName.isNotEmpty()) {
            deviceInfo.deviceName
        } else {
            "Dispositivo sconosciuto"
        }

        // Indirizzo MAC
        holder.deviceAddress.text = deviceInfo.deviceAddress

        // RSSI (intensità segnale)
        holder.deviceRssi.text = "${deviceInfo.rssi} dBm"

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
        val iconResource = when (deviceInfo.getDeviceType()) {
            "Telefono" -> android.R.drawable.stat_sys_phone_call
            "Audio" -> android.R.drawable.ic_media_play
            "Computer" -> android.R.drawable.stat_sys_download
            else -> android.R.drawable.stat_sys_data_bluetooth
        }
        holder.deviceIcon.setImageResource(iconResource)

        // Click listener per connessione
        holder.itemView.setOnClickListener {
            onDeviceClick(deviceInfo)
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
        } else {
            // Aggiungi nuovo dispositivo
            devices.add(deviceInfo)
            notifyItemInserted(devices.size - 1)
        }
    }

    /**
     * Pulisce la lista dei dispositivi
     */
    fun clearDevices() {
        val size = devices.size
        devices.clear()
        notifyItemRangeRemoved(0, size)
    }

    /**
     * Restituisce tutti i dispositivi nella lista
     */
    fun getAllDevices(): List<BluetoothDeviceInfo> = devices.toList()
}