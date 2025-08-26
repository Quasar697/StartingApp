package com.dji.samplev5.startingapp

import java.text.SimpleDateFormat
import java.util.*

/**
 * Rappresenta un messaggio nella chat
 */
data class ChatMessage(
    val text: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis(),
    val isDelivered: Boolean = false,
    val deviceAddress: String? = null
) {
    enum class MessageType {
        SENT,       // Messaggio inviato da noi
        RECEIVED,   // Messaggio ricevuto dall'altro dispositivo
        SYSTEM      // Messaggio di sistema (connessione, errori, etc.)
    }

    /**
     * Formatta il timestamp in formato leggibile
     */
    fun getFormattedTime(): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    /**
     * Formatta il timestamp con data completa
     */
    fun getFormattedDateTime(): String {
        val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    /**
     * Restituisce un'anteprima del messaggio (max 50 caratteri)
     */
    fun getPreview(): String {
        return if (text.length > 50) {
            text.substring(0, 47) + "..."
        } else {
            text
        }
    }

    /**
     * Controlla se il messaggio è di oggi
     */
    fun isToday(): Boolean {
        val today = Calendar.getInstance()
        val messageDate = Calendar.getInstance().apply { timeInMillis = timestamp }

        return today.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        /**
         * Crea un messaggio di sistema
         */
        fun createSystemMessage(text: String): ChatMessage {
            return ChatMessage(
                text = text,
                type = MessageType.SYSTEM
            )
        }

        /**
         * Crea un messaggio inviato
         */
        fun createSentMessage(text: String, deviceAddress: String? = null): ChatMessage {
            return ChatMessage(
                text = text,
                type = MessageType.SENT,
                deviceAddress = deviceAddress
            )
        }

        /**
         * Crea un messaggio ricevuto
         */
        fun createReceivedMessage(text: String, deviceAddress: String? = null): ChatMessage {
            return ChatMessage(
                text = text,
                type = MessageType.RECEIVED,
                deviceAddress = deviceAddress
            )
        }
    }
}