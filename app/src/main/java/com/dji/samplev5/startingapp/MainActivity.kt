package com.dji.samplev5.startingapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Build

class MainActivity : Activity(), BluetoothConnectionManager.ConnectionListener {

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSIONS = 2
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var btnBluetooth: Button
    private lateinit var btnScan: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvDevicesTitle: TextView
    private lateinit var tvNoDevices: TextView
    private lateinit var progressScan: ProgressBar
    private lateinit var rvDevices: RecyclerView
    private lateinit var deviceAdapter: BluetoothDeviceAdapter

    // Variabili per la connessione
    private lateinit var connectionManager: BluetoothConnectionManager
    private var connectedDevice: BluetoothDeviceInfo? = null

    private var isScanning = false

    // BroadcastReceiver per monitorare i cambiamenti di stato del Bluetooth
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            updateUI()
                            stopScan()
                            // Disconnetti se Bluetooth viene disattivato
                            if (connectedDevice != null) {
                                connectionManager.disconnect()
                                connectedDevice = null
                            }
                            Toast.makeText(this@MainActivity, "Bluetooth disattivato", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothAdapter.STATE_ON -> {
                            updateUI()
                            Toast.makeText(this@MainActivity, "Bluetooth attivato", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothAdapter.STATE_TURNING_OFF -> {
                            tvStatus.text = "Disattivazione in corso..."
                            stopScan()
                        }
                        BluetoothAdapter.STATE_TURNING_ON -> {
                            tvStatus.text = "Attivazione in corso..."
                        }
                    }
                }
            }
        }
    }

    // BroadcastReceiver per la scoperta di dispositivi con debug migliorato
    private val deviceDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                    device?.let {
                        Log.d("BluetoothScan", "Dispositivo trovato: ${it.address}, RSSI: $rssi")

                        try {
                            val deviceName = if (hasBluetoothPermission()) {
                                it.name ?: "Nome sconosciuto"
                            } else {
                                "Nome sconosciuto"
                            }
                            Log.d("BluetoothScan", "Nome dispositivo: $deviceName")
                        } catch (e: SecurityException) {
                            Log.w("BluetoothScan", "Impossibile ottenere nome: ${e.message}")
                        }

                        val deviceInfo = BluetoothDeviceInfo(it, rssi, this@MainActivity)
                        deviceAdapter.addDevice(deviceInfo)
                        updateDeviceListVisibility()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    isScanning = true
                    updateScanUI()
                    Log.d("BluetoothScan", "Discovery iniziata")
                    Toast.makeText(this@MainActivity, "Cercando dispositivi nelle vicinanze...", Toast.LENGTH_SHORT).show()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isScanning = false
                    updateScanUI()
                    Log.d("BluetoothScan", "Discovery terminata")

                    val foundDevices = deviceAdapter.getAllDevices().size
                    Log.d("BluetoothScan", "Discovery terminata. Trovati $foundDevices dispositivi totali")

                    if (foundDevices == 0) {
                        showNoDevicesMessage()
                        showScanHelpDialog()
                    } else {
                        Toast.makeText(this@MainActivity, "Scansione completata: $foundDevices dispositivi", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = ContextCompat.getColor(this, android.R.color.white)
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.darker_gray)
        }


        initializeViews()
        initializeBluetooth()

        // Inizializza il connection manager
        connectionManager = BluetoothConnectionManager(this, bluetoothAdapter, this)

        setupRecyclerView()
        setupClickListeners()
        checkPermissions()
        updateUI()

        // Registra i BroadcastReceiver
        registerBluetoothReceivers()
    }

    private fun initializeViews() {
        btnBluetooth = findViewById(R.id.btn_bluetooth)
        btnScan = findViewById(R.id.btn_scan)
        tvStatus = findViewById(R.id.tv_status)
        tvDevicesTitle = findViewById(R.id.tv_devices_title)
        tvNoDevices = findViewById(R.id.tv_no_devices)
        progressScan = findViewById(R.id.progress_scan)
        rvDevices = findViewById(R.id.rv_devices)
    }

    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!::bluetoothAdapter.isInitialized) {
            Toast.makeText(this, "Bluetooth non supportato su questo dispositivo", Toast.LENGTH_LONG).show()
            btnBluetooth.isEnabled = false
            btnScan.isEnabled = false
            return
        }
    }

    private fun setupRecyclerView() {
        deviceAdapter = BluetoothDeviceAdapter(mutableListOf()) { deviceInfo ->
            onDeviceClick(deviceInfo)
        }
        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = deviceAdapter
    }

    private fun setupClickListeners() {
        btnBluetooth.setOnClickListener {
            toggleBluetooth()
        }

        btnScan.setOnClickListener {
            if (isScanning) {
                stopScan()
            } else {
                startScan()
            }
        }

        // TEMPORANEO: Long press sul pulsante scan per diagnostica
        btnScan.setOnLongClickListener {
            runDiagnostics()
            true
        }
    }

    private fun onDeviceClick(deviceInfo: BluetoothDeviceInfo) {
        if (connectedDevice?.deviceAddress == deviceInfo.deviceAddress) {
            // Se è già connesso, mostra opzioni chat
            showDisconnectDialog(deviceInfo)
        } else {
            // Altrimenti, mostra dialog di connessione
            showConnectionDialog(deviceInfo)
        }
    }

    private fun showConnectionDialog(deviceInfo: BluetoothDeviceInfo) {
        val deviceName = if (deviceInfo.deviceName.contains("sconosciuto")) {
            "Dispositivo ${deviceInfo.deviceAddress.takeLast(5)}"
        } else {
            deviceInfo.deviceName
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Connetti a dispositivo")
            .setMessage("Vuoi connetterti a:\n$deviceName\n${deviceInfo.deviceAddress}?")
            .setPositiveButton("Connetti") { _, _ ->
                connectToDevice(deviceInfo)
            }
            .setNegativeButton("Annulla", null)
            .setNeutralButton("Info") { _, _ ->
                showDeviceInfo(deviceInfo)
            }
            .show()
    }

    private fun showDisconnectDialog(deviceInfo: BluetoothDeviceInfo) {
        val deviceName = if (deviceInfo.deviceName.contains("sconosciuto")) {
            "Dispositivo ${deviceInfo.deviceAddress.takeLast(5)}"
        } else {
            deviceInfo.deviceName
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Dispositivo Connesso")
            .setMessage("Connesso a:\n$deviceName\n\nCosa vuoi fare?")
            .setPositiveButton("💬 Chat") { _, _ ->
                openChatActivity(deviceInfo)
            }
            .setNegativeButton("🔌 Disconnetti") { _, _ ->
                disconnectFromDevice()
            }
            .setNeutralButton("📊 Info") { _, _ ->
                showDeviceInfo(deviceInfo)
            }
            .show()
    }

    private fun openChatActivity(deviceInfo: BluetoothDeviceInfo) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_DEVICE_ADDRESS, deviceInfo.deviceAddress)
            putExtra(ChatActivity.EXTRA_DEVICE_NAME, deviceInfo.deviceName)
        }
        startActivity(intent)
    }

    private fun showDeviceInfo(deviceInfo: BluetoothDeviceInfo) {
        val info = deviceInfo.getDebugInfo()
        android.app.AlertDialog.Builder(this)
            .setTitle("Informazioni Dispositivo")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSendDataDialog() {
        if (connectedDevice == null) {
            Toast.makeText(this, "Nessun dispositivo connesso", Toast.LENGTH_SHORT).show()
            return
        }

        val input = android.widget.EditText(this)
        input.hint = "Inserisci testo da inviare..."
        input.setText("Hello from Android!")

        android.app.AlertDialog.Builder(this)
            .setTitle("Invia dati")
            .setMessage("Invia dati a: ${connectedDevice!!.deviceName}")
            .setView(input)
            .setPositiveButton("Invia") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendDataToDevice(text)
                } else {
                    Toast.makeText(this, "Inserisci del testo", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun sendDataToDevice(data: String) {
        if (connectionManager.sendData(data)) {
            Toast.makeText(this, "Dati inviati: $data", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Dati inviati con successo: $data")
        } else {
            Toast.makeText(this, "Errore nell'invio dati", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Errore nell'invio dati: $data")
        }
    }

    private fun connectToDevice(deviceInfo: BluetoothDeviceInfo) {
        Log.d("MainActivity", "Tentativo connessione a: ${deviceInfo.deviceAddress}")
        connectionManager.connectToDevice(deviceInfo)

        // Ferma la scansione durante la connessione
        stopScan()
    }

    private fun disconnectFromDevice() {
        Log.d("MainActivity", "Disconnessione...")
        connectionManager.disconnect()
    }

    private fun updateDeviceConnectionStatus() {
        // Aggiorna lo stato di connessione nell'adapter
        deviceAdapter.updateConnectionStatus(connectedDevice?.deviceAddress)
        deviceAdapter.notifyDataSetChanged()
    }

    private fun loadPairedDevices() {
        if (!bluetoothAdapter.isEnabled) {
            return
        }

        if (!hasBluetoothPermission()) {
            Toast.makeText(this, "Permessi Bluetooth necessari", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pairedDevices = bluetoothAdapter.bondedDevices
            Log.d("BluetoothDebug", "Dispositivi accoppiati trovati: ${pairedDevices.size}")

            pairedDevices?.forEach { device ->
                val deviceInfo = BluetoothDeviceInfo(device, rssi = -50, context = this)
                Log.d("BluetoothDebug", "Dispositivo accoppiato: ${device.address} - Nome: '${device.name}'")
                deviceAdapter.addDevice(deviceInfo)
            }

            if (pairedDevices?.isNotEmpty() == true) {
                updateDeviceListVisibility()
                Toast.makeText(this, "Caricati ${pairedDevices.size} dispositivi accoppiati", Toast.LENGTH_SHORT).show()
            }

        } catch (e: SecurityException) {
            Log.e("BluetoothDebug", "SecurityException nel caricare dispositivi accoppiati: ${e.message}")
            Toast.makeText(this, "Errore permessi per dispositivi accoppiati", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Permessi Bluetooth tradizionali
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Permessi per localizzazione (necessario per la scansione Bluetooth)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Permessi per Android 12+ (API 31+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun toggleBluetooth() {
        if (!::bluetoothAdapter.isInitialized) {
            Toast.makeText(this, "Bluetooth non disponibile", Toast.LENGTH_SHORT).show()
            return
        }

        if (bluetoothAdapter.isEnabled) {
            // Disattiva Bluetooth - Dal Android 13+ (API 33) non è più consentito
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(this, "Su Android 13+ devi disattivare il Bluetooth manualmente dalle impostazioni", Toast.LENGTH_LONG).show()
                val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                startActivity(intent)
            } else {
                if (hasBluetoothPermission()) {
                    try {
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
                            val success = bluetoothAdapter.disable()
                            if (success) {
                                Toast.makeText(this, "Disattivazione Bluetooth in corso...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Impossibile disattivare il Bluetooth", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Permesso BLUETOOTH_ADMIN richiesto", Toast.LENGTH_SHORT).show()
                        }

                        btnBluetooth.postDelayed({
                            updateUI()
                        }, 1000)
                    } catch (e: SecurityException) {
                        Toast.makeText(this, "Permesso negato per disattivare il Bluetooth", Toast.LENGTH_LONG).show()
                        requestBluetoothPermissions()
                    }
                } else {
                    requestBluetoothPermissions()
                }
            }
        } else {
            if (hasBluetoothPermission()) {
                try {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                } catch (e: SecurityException) {
                    Toast.makeText(this, "Permesso negato per attivare il Bluetooth", Toast.LENGTH_LONG).show()
                    requestBluetoothPermissions()
                }
            } else {
                requestBluetoothPermissions()
            }
        }
    }

    private fun startScan() {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Attiva prima il Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        // Verifica se i servizi di localizzazione sono attivi
        if (!isLocationEnabled()) {
            showLocationRequiredDialog()
            return
        }

        if (!hasBluetoothScanPermission()) {
            requestBluetoothPermissions()
            return
        }

        try {
            deviceAdapter.clearDevices()
            hideNoDevicesMessage()

            // Log per debug
            Log.d("BluetoothScan", "Inizio scansione dispositivi...")

            // Carica prima i dispositivi accoppiati
            loadPairedDevices()

            // Ferma eventuali scansioni in corso
            if (bluetoothAdapter.isDiscovering) {
                Log.d("BluetoothScan", "Fermando scansione precedente...")
                bluetoothAdapter.cancelDiscovery()

                // Aspetta un po' prima di riavviare
                btnScan.postDelayed({
                    startBluetoothDiscovery()
                }, 1000)
            } else {
                startBluetoothDiscovery()
            }

        } catch (e: SecurityException) {
            Log.e("BluetoothScan", "SecurityException: ${e.message}")
            Toast.makeText(this, "Permesso negato per la scansione", Toast.LENGTH_LONG).show()
            requestBluetoothPermissions()
        }
    }

    private fun startBluetoothDiscovery() {
        try {
            Log.d("BluetoothScan", "Avvio discovery Bluetooth...")

            val success = bluetoothAdapter.startDiscovery()
            if (success) {
                Log.d("BluetoothScan", "Discovery avviata con successo")
                Toast.makeText(this, "Scansione nuovi dispositivi avviata...", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("BluetoothScan", "Impossibile avviare discovery")
                Toast.makeText(this, "Impossibile avviare la scansione", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Log.e("BluetoothScan", "SecurityException in startDiscovery: ${e.message}")
            Toast.makeText(this, "Errore permessi nella scansione", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            val mode = android.provider.Settings.Secure.getInt(
                contentResolver,
                android.provider.Settings.Secure.LOCATION_MODE,
                android.provider.Settings.Secure.LOCATION_MODE_OFF
            )
            mode != android.provider.Settings.Secure.LOCATION_MODE_OFF
        }
    }

    private fun showLocationRequiredDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Servizi di localizzazione richiesti")
            .setMessage("Per trovare nuovi dispositivi Bluetooth, devi attivare i servizi di localizzazione.\n\nQuesto è un requisito di Android per la privacy, anche se l'app non userà la tua posizione.")
            .setPositiveButton("Apri Impostazioni") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Solo dispositivi accoppiati") { _, _ ->
                // Carica solo dispositivi già accoppiati
                deviceAdapter.clearDevices()
                loadPairedDevices()
                if (deviceAdapter.getAllDevices().isEmpty()) {
                    showNoDevicesMessage()
                }
            }
            .setNeutralButton("Annulla", null)
            .show()
    }

    private fun showScanHelpDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Nessun dispositivo trovato")
            .setMessage("Suggerimenti per trovare dispositivi:\n\n" +
                    "📱 Metti altri telefoni in modalità 'rilevabile'\n" +
                    "🎧 Attiva la modalità pairing su cuffie/speaker\n" +
                    "📍 Assicurati che la localizzazione sia attiva\n" +
                    "📡 Avvicinati ai dispositivi (< 10 metri)\n" +
                    "🔄 Riprova la scansione")
            .setPositiveButton("Riprova") { _, _ ->
                startScan()
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun stopScan() {
        if (isScanning && ::bluetoothAdapter.isInitialized) {
            try {
                bluetoothAdapter.cancelDiscovery()
                Toast.makeText(this, "Scansione interrotta", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                // Ignora errori di permesso durante lo stop
            }
        }
    }

    private fun runDiagnostics() {
        val sb = StringBuilder()
        sb.append("=== DIAGNOSTICA BLUETOOTH ===\n")
        sb.append("Android Version: ${android.os.Build.VERSION.SDK_INT}\n")
        sb.append("Bluetooth Adapter: ${if (::bluetoothAdapter.isInitialized) "OK" else "NON INIZIALIZZATO"}\n")

        if (::bluetoothAdapter.isInitialized) {
            sb.append("Bluetooth Enabled: ${bluetoothAdapter.isEnabled}\n")
            sb.append("Discovery Running: ${bluetoothAdapter.isDiscovering}\n")
        }

        // Permessi
        sb.append("\n=== PERMESSI ===\n")
        sb.append("BLUETOOTH: ${checkPermission(Manifest.permission.BLUETOOTH)}\n")
        sb.append("BLUETOOTH_ADMIN: ${checkPermission(Manifest.permission.BLUETOOTH_ADMIN)}\n")
        sb.append("ACCESS_COARSE_LOCATION: ${checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)}\n")
        sb.append("ACCESS_FINE_LOCATION: ${checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)}\n")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            sb.append("BLUETOOTH_CONNECT: ${checkPermission(Manifest.permission.BLUETOOTH_CONNECT)}\n")
            sb.append("BLUETOOTH_SCAN: ${checkPermission(Manifest.permission.BLUETOOTH_SCAN)}\n")
        }

        // Localizzazione
        sb.append("\n=== LOCALIZZAZIONE ===\n")
        sb.append("Location Services: ${if (isLocationEnabled()) "ATTIVI" else "DISATTIVI"}\n")

        // Dispositivi accoppiati
        sb.append("\n=== DISPOSITIVI ACCOPPIATI ===\n")
        try {
            if (hasBluetoothPermission()) {
                val pairedDevices = bluetoothAdapter.bondedDevices
                sb.append("Count: ${pairedDevices?.size ?: 0}\n")
                pairedDevices?.forEach { device ->
                    sb.append("- ${device.address}: ${device.name ?: "Nome sconosciuto"}\n")
                }
            } else {
                sb.append("Permessi insufficienti\n")
            }
        } catch (e: SecurityException) {
            sb.append("Errore permessi: ${e.message}\n")
        }

        // Mostra risultato
        android.app.AlertDialog.Builder(this)
            .setTitle("Diagnostica Bluetooth")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .setNeutralButton("Copia log") { _, _ ->
                Log.i("BluetoothDiagnostic", sb.toString())
                Toast.makeText(this, "Log copiato in Logcat", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun checkPermission(permission: String): String {
        return if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            "CONCESSO"
        } else {
            "NEGATO"
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS)
    }

    private fun updateUI() {
        if (!::bluetoothAdapter.isInitialized) return

        if (bluetoothAdapter.isEnabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                btnBluetooth.text = "Apri Impostazioni Bluetooth"
            } else {
                btnBluetooth.text = "Disattiva Bluetooth"
            }

            // Mostra stato connessione
            if (connectedDevice != null) {
                val deviceName = if (connectedDevice!!.deviceName.contains("sconosciuto")) {
                    "Dispositivo ${connectedDevice!!.deviceAddress.takeLast(5)}"
                } else {
                    connectedDevice!!.deviceName
                }
                tvStatus.text = "Bluetooth attivo - Connesso a: $deviceName"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            } else {
                tvStatus.text = "Bluetooth attivo"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }

            btnScan.isEnabled = true
        } else {
            btnBluetooth.text = "Attiva Bluetooth"
            tvStatus.text = "Bluetooth non attivo"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnScan.isEnabled = false
            stopScan()
            hideDeviceList()

            // Disconnetti se Bluetooth viene disattivato
            if (connectedDevice != null) {
                connectionManager.disconnect()
                connectedDevice = null
            }
        }
    }

    private fun updateScanUI() {
        if (isScanning) {
            btnScan.text = "Ferma scansione"
            progressScan.visibility = View.VISIBLE
        } else {
            btnScan.text = "Cerca dispositivi"
            progressScan.visibility = View.GONE
        }
    }

    private fun updateDeviceListVisibility() {
        if (deviceAdapter.getAllDevices().isNotEmpty()) {
            tvDevicesTitle.visibility = View.VISIBLE
            rvDevices.visibility = View.VISIBLE
            tvNoDevices.visibility = View.GONE
        }
    }

    private fun showNoDevicesMessage() {
        tvDevicesTitle.visibility = View.GONE
        rvDevices.visibility = View.GONE
        tvNoDevices.visibility = View.VISIBLE
    }

    private fun hideNoDevicesMessage() {
        tvNoDevices.visibility = View.GONE
    }

    private fun hideDeviceList() {
        tvDevicesTitle.visibility = View.GONE
        rvDevices.visibility = View.GONE
        tvNoDevices.visibility = View.GONE
        deviceAdapter.clearDevices()
    }

    private fun registerBluetoothReceivers() {
        try {
            val stateFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(bluetoothStateReceiver, stateFilter)

            val discoveryFilter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            registerReceiver(deviceDiscoveryReceiver, discoveryFilter)
        } catch (e: IllegalArgumentException) {
            // I receiver sono già registrati
        }
    }

    // IMPLEMENTAZIONE DELL'INTERFACCIA ConnectionListener

    override fun onConnectionStarted(device: BluetoothDevice) {
        runOnUiThread {
            Toast.makeText(this, "Connessione in corso...", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Connessione avviata con: ${device.address}")
        }
    }

    override fun onConnectionSuccess(device: BluetoothDevice) {
        runOnUiThread {
            val deviceInfo = deviceAdapter.getAllDevices().find { it.deviceAddress == device.address }
            connectedDevice = deviceInfo

            val deviceName = try {
                if (hasBluetoothPermission()) device.name ?: "Dispositivo sconosciuto"
                else "Dispositivo sconosciuto"
            } catch (e: SecurityException) {
                "Dispositivo sconosciuto"
            }

            Toast.makeText(this, "Connesso a: $deviceName", Toast.LENGTH_LONG).show()
            Log.d("MainActivity", "Connessione riuscita con: ${device.address}")

            updateDeviceConnectionStatus()
            updateUI()

            // NUOVO: Mostra dialog per aprire chat automaticamente
            connectedDevice?.let { deviceInfo ->
                android.app.AlertDialog.Builder(this)
                    .setTitle("🎉 Connessione riuscita!")
                    .setMessage("Connesso a: $deviceName\n\nVuoi aprire la chat?")
                    .setPositiveButton("💬 Apri Chat") { _, _ ->
                        openChatActivity(deviceInfo)
                    }
                    .setNegativeButton("Dopo", null)
                    .show()
            }
        }
    }

    override fun onConnectionFailed(device: BluetoothDevice, error: String) {
        runOnUiThread {
            Toast.makeText(this, "Connessione fallita: $error", Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "Connessione fallita con ${device.address}: $error")

            connectedDevice = null
            updateDeviceConnectionStatus()
        }
    }

    override fun onDisconnected(device: BluetoothDevice) {
        runOnUiThread {
            Toast.makeText(this, "Dispositivo disconnesso", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Disconnesso da: ${device.address}")

            connectedDevice = null
            updateDeviceConnectionStatus()
            updateUI()
        }
    }

    override fun onDataReceived(device: BluetoothDevice, data: String) {
        runOnUiThread {
            Log.d("MainActivity", "Dati ricevuti da ${device.address}: $data")

            // Mostra i dati ricevuti in un toast
            Toast.makeText(this, "Ricevuto: $data", Toast.LENGTH_SHORT).show()
        }
    }

    // OVERRIDE METODI ACTIVITY

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                updateUI()
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "Bluetooth attivato", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Attivazione Bluetooth annullata", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    Toast.makeText(this, "Permessi concessi", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Permessi necessari per il funzionamento", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        registerBluetoothReceivers()
    }

    override fun onPause() {
        super.onPause()
        // Non rimuoviamo i receiver qui per continuare a monitorare
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        connectionManager.disconnect()
        try {
            unregisterReceiver(bluetoothStateReceiver)
            unregisterReceiver(deviceDiscoveryReceiver)
        } catch (e: IllegalArgumentException) {
            // I receiver non erano registrati
        }
    }
}