package com.dji.samplev5.startingapp

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Build
import androidx.core.content.ContextCompat

class ChatActivity : Activity(), BluetoothConnectionManager.ConnectionListener {

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_NAME = "device_name"
        private const val TAG = "ChatActivity"
    }

    // Views
    private lateinit var ivBack: ImageView
    private lateinit var tvDeviceName: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var ivConnectionIndicator: ImageView
    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnQuickMsg: Button

    // Chat components
    private lateinit var messagesAdapter: ChatMessagesAdapter
    private lateinit var connectionManager: BluetoothConnectionManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    // Device info
    private var deviceAddress: String? = null
    private var deviceName: String? = null
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = ContextCompat.getColor(this, android.R.color.white)
            window.statusBarColor = ContextCompat.getColor(this, R.color.chat_header_color) // #2196F3
        }
        // Ottieni info dispositivo dall'intent
        deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)

        if (deviceAddress == null) {
            Toast.makeText(this, "Errore: indirizzo dispositivo mancante", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        initializeBluetooth()
        setupRecyclerView()
        setupClickListeners()
        updateUI()

        // Aggiungi messaggio di benvenuto
        addSystemMessage("Chat avviata con: $deviceName")
        addSystemMessage("Connessione in corso...")
    }

    private fun initializeViews() {
        ivBack = findViewById(R.id.iv_back)
        tvDeviceName = findViewById(R.id.tv_device_name)
        tvConnectionStatus = findViewById(R.id.tv_connection_status)
        ivConnectionIndicator = findViewById(R.id.iv_connection_indicator)
        rvMessages = findViewById(R.id.rv_messages)
        etMessage = findViewById(R.id.et_message)
        btnSend = findViewById(R.id.btn_send)
        btnQuickMsg = findViewById(R.id.btn_quick_msg)
    }

    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        connectionManager = BluetoothConnectionManager(this, bluetoothAdapter, this)
    }

    private fun setupRecyclerView() {
        messagesAdapter = ChatMessagesAdapter(mutableListOf())
        rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = messagesAdapter
        }
    }

    private fun setupClickListeners() {
        ivBack.setOnClickListener {
            finish()
        }

        btnSend.setOnClickListener {
            sendMessage()
        }

        etMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        btnQuickMsg.setOnClickListener {
            showQuickMessageDialog()
        }
    }

    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Inserisci un messaggio", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isConnected) {
            Toast.makeText(this, "Dispositivo non connesso", Toast.LENGTH_SHORT).show()
            return
        }

        // Aggiungi messaggio alla chat come "inviato"
        val message = ChatMessage.createSentMessage(messageText, deviceAddress)
        messagesAdapter.addMessage(message)
        scrollToBottom()

        // Invia il messaggio tramite Bluetooth
        val success = connectionManager.sendData(messageText)
        if (success) {
            Log.d(TAG, "Messaggio inviato: $messageText")
            // Pulisci il campo di input
            etMessage.text.clear()
        } else {
            Log.e(TAG, "Errore nell'invio del messaggio: $messageText")
            addSystemMessage("Errore nell'invio del messaggio")
        }
    }

    private fun showQuickMessageDialog() {
        val quickMessages = arrayOf(
            "👋 Ciao!",
            "👍 OK",
            "❓ Come stai?",
            "📍 Dove sei?",
            "⏰ Che ora è?",
            "✅ Fatto!",
            "❌ No",
            "🔥 Perfetto!",
            "💡 Ho un'idea",
            "📞 Chiamami"
        )

        android.app.AlertDialog.Builder(this)
            .setTitle("Messaggi rapidi")
            .setItems(quickMessages) { _, which ->
                val selectedMessage = quickMessages[which]
                etMessage.setText(selectedMessage)
                sendMessage()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun addSystemMessage(text: String) {
        val message = ChatMessage.createSystemMessage(text)
        messagesAdapter.addMessage(message)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        if (messagesAdapter.itemCount > 0) {
            rvMessages.scrollToPosition(messagesAdapter.itemCount - 1)
        }
    }

    private fun updateUI() {
        tvDeviceName.text = deviceName ?: "Dispositivo sconosciuto"

        if (isConnected) {
            tvConnectionStatus.text = "🟢 Connesso"
            ivConnectionIndicator.setImageResource(android.R.drawable.presence_online)
            btnSend.isEnabled = true
            etMessage.isEnabled = true
        } else {
            tvConnectionStatus.text = "🔴 Disconnesso"
            ivConnectionIndicator.setImageResource(android.R.drawable.presence_offline)
            btnSend.isEnabled = false
            etMessage.isEnabled = false
        }
    }

    private fun showConnectionStats() {
        val stats = messagesAdapter.getChatStats()
        val info = "=== STATISTICHE CHAT ===\n" +
                "Dispositivo: $deviceName\n" +
                "Indirizzo: $deviceAddress\n" +
                "Stato: ${if (isConnected) "Connesso" else "Disconnesso"}\n" +
                "Messaggi: $stats\n" +
                "Durata sessione: ${getSessionDuration()}"

        android.app.AlertDialog.Builder(this)
            .setTitle("Statistiche Chat")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun getSessionDuration(): String {
        // Calcola durata approssimativa dalla prima connessione
        val messages = messagesAdapter.getAllMessages()
        val firstMessage = messages.firstOrNull()
        val lastMessage = messages.lastOrNull()

        return if (firstMessage != null && lastMessage != null) {
            val duration = (lastMessage.timestamp - firstMessage.timestamp) / 1000 / 60
            "${duration}m"
        } else {
            "< 1m"
        }
    }

    // IMPLEMENTAZIONE ConnectionListener

    override fun onConnectionStarted(device: BluetoothDevice) {
        runOnUiThread {
            addSystemMessage("Connessione in corso...")
        }
    }

    override fun onConnectionSuccess(device: BluetoothDevice) {
        runOnUiThread {
            isConnected = true
            updateUI()
            addSystemMessage("✅ Connesso! Puoi iniziare a chattare.")
            Log.d(TAG, "Connessione stabilita con: ${device.address}")
        }
    }

    override fun onConnectionFailed(device: BluetoothDevice, error: String) {
        runOnUiThread {
            isConnected = false
            updateUI()
            addSystemMessage("❌ Connessione fallita: $error")
            Log.e(TAG, "Connessione fallita: $error")

            // Opzione per ritentare
            android.app.AlertDialog.Builder(this)
                .setTitle("Connessione fallita")
                .setMessage("Impossibile connettersi al dispositivo.\n\nErrore: $error")
                .setPositiveButton("Riprova") { _, _ ->
                    connectToDevice()
                }
                .setNegativeButton("Chiudi") { _, _ ->
                    finish()
                }
                .show()
        }
    }

    override fun onDisconnected(device: BluetoothDevice) {
        runOnUiThread {
            isConnected = false
            updateUI()
            addSystemMessage("🔌 Dispositivo disconnesso")
            Log.d(TAG, "Dispositivo disconnesso")
        }
    }

    override fun onDataReceived(device: BluetoothDevice, data: String) {
        runOnUiThread {
            // Aggiungi messaggio ricevuto alla chat
            val message = ChatMessage.createReceivedMessage(data.trim(), device.address)
            messagesAdapter.addMessage(message)
            scrollToBottom()

            Log.d(TAG, "Messaggio ricevuto: $data")

            // Feedback visivo/sonoro (opzionale)
            // playNotificationSound()
        }
    }

    private fun connectToDevice() {
        deviceAddress?.let { address ->
            // Crea un BluetoothDevice dall'indirizzo
            val device = bluetoothAdapter.getRemoteDevice(address)
            val deviceInfo = BluetoothDeviceInfo(device, context = this)
            connectionManager.connectToDevice(deviceInfo)
        }
    }

    override fun onResume() {
        super.onResume()
        // Tenta la connessione se non già connesso
        if (!isConnected && deviceAddress != null) {
            connectToDevice()
        }
    }

    override fun onBackPressed() {
        // Chiedi conferma prima di uscire se ci sono messaggi
        if (messagesAdapter.itemCount > 2) { // Più di 2 messaggi di sistema iniziali
            android.app.AlertDialog.Builder(this)
                .setTitle("Esci dalla chat")
                .setMessage("Vuoi davvero uscire dalla chat?")
                .setPositiveButton("Esci") { _, _ ->
                    super.onBackPressed()
                }
                .setNegativeButton("Rimani", null)
                .setNeutralButton("Statistiche") { _, _ ->
                    showConnectionStats()
                }
                .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionManager.disconnect()
        Log.d(TAG, "ChatActivity distrutta, connessione chiusa")
    }
}