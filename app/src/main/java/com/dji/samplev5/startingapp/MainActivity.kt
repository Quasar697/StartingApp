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
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {

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

    // BroadcastReceiver per la scoperta di dispositivi
    private val deviceDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                    device?.let {
                        val deviceInfo = BluetoothDeviceInfo(it, rssi, this@MainActivity)
                        deviceAdapter.addDevice(deviceInfo)
                        updateDeviceListVisibility()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    isScanning = true
                    updateScanUI()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isScanning = false
                    updateScanUI()

                    if (deviceAdapter.getAllDevices().isEmpty()) {
                        showNoDevicesMessage()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        initializeBluetooth()
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
    }

    private fun onDeviceClick(deviceInfo: BluetoothDeviceInfo) {
        Toast.makeText(this,
            "Selezionato: ${deviceInfo.deviceName}\nIndirizzo: ${deviceInfo.deviceAddress}",
            Toast.LENGTH_LONG).show()

        // Qui in futuro aggiungeremo la logica di connessione
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

        if (!hasBluetoothScanPermission()) {
            requestBluetoothPermissions()
            return
        }

        try {
            deviceAdapter.clearDevices()
            hideNoDevicesMessage()

            if (bluetoothAdapter.startDiscovery()) {
                Toast.makeText(this, "Scansione avviata...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Impossibile avviare la scansione", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permesso negato per la scansione", Toast.LENGTH_LONG).show()
            requestBluetoothPermissions()
        }
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
            tvStatus.text = "Bluetooth attivo"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnScan.isEnabled = true
        } else {
            btnBluetooth.text = "Attiva Bluetooth"
            tvStatus.text = "Bluetooth non attivo"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnScan.isEnabled = false
            stopScan()
            hideDeviceList()
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
        try {
            unregisterReceiver(bluetoothStateReceiver)
            unregisterReceiver(deviceDiscoveryReceiver)
        } catch (e: IllegalArgumentException) {
            // I receiver non erano registrati
        }
    }
}