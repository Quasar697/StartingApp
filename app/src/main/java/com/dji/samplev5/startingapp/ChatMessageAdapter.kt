package com.dji.samplev5.startingapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatMessagesAdapter(
    private val messages: MutableList<ChatMessage>
) : RecyclerView.Adapter<ChatMessagesAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Layout containers
        val layoutSent: LinearLayout = itemView.findViewById(R.id.layout_sent)
        val layoutReceived: LinearLayout = itemView.findViewById(R.id.layout_received)
        val layoutSystem: LinearLayout = itemView.findViewById(R.id.layout_system)

        // Sent message views
        val tvSentMessage: TextView = itemView.findViewById(R.id.tv_sent_message)
        val tvSentTime: TextView = itemView.findViewById(R.id.tv_sent_time)

        // Received message views
        val tvReceivedMessage: TextView = itemView.findViewById(R.id.tv_received_message)
        val tvReceivedTime: TextView = itemView.findViewById(R.id.tv_received_time)

        // System message view
        val tvSystemMessage: TextView = itemView.findViewById(R.id.tv_system_message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        // Nascondi tutti i layout prima
        holder.layoutSent.visibility = View.GONE
        holder.layoutReceived.visibility = View.GONE
        holder.layoutSystem.visibility = View.GONE

        when (message.type) {
            ChatMessage.MessageType.SENT -> {
                holder.layoutSent.visibility = View.VISIBLE
                holder.tvSentMessage.text = message.text
                holder.tvSentTime.text = message.getFormattedTime()
            }

            ChatMessage.MessageType.RECEIVED -> {
                holder.layoutReceived.visibility = View.VISIBLE
                holder.tvReceivedMessage.text = message.text
                holder.tvReceivedTime.text = message.getFormattedTime()
            }

            ChatMessage.MessageType.SYSTEM -> {
                holder.layoutSystem.visibility = View.VISIBLE
                holder.tvSystemMessage.text = message.text
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    /**
     * Aggiunge un nuovo messaggio e scrolla alla fine
     */
    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    /**
     * Aggiunge più messaggi contemporaneamente
     */
    fun addMessages(newMessages: List<ChatMessage>) {
        val startPosition = messages.size
        messages.addAll(newMessages)
        notifyItemRangeInserted(startPosition, newMessages.size)
    }

    /**
     * Pulisce tutti i messaggi
     */
    fun clearMessages() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }

    /**
     * Restituisce tutti i messaggi
     */
    fun getAllMessages(): List<ChatMessage> = messages.toList()

    /**
     * Aggiorna il delivery status di un messaggio
     */
    fun updateMessageDeliveryStatus(position: Int, isDelivered: Boolean) {
        if (position in 0 until messages.size) {
            // Crea una nuova istanza con lo stato aggiornato
            val oldMessage = messages[position]
            messages[position] = oldMessage.copy(isDelivered = isDelivered)
            notifyItemChanged(position)
        }
    }

    /**
     * Trova l'indice dell'ultimo messaggio inviato
     */
    fun getLastSentMessageIndex(): Int {
        for (i in messages.size - 1 downTo 0) {
            if (messages[i].type == ChatMessage.MessageType.SENT) {
                return i
            }
        }
        return -1
    }

    /**
     * Conta i messaggi per tipo
     */
    fun getMessageCountByType(type: ChatMessage.MessageType): Int {
        return messages.count { it.type == type }
    }

    /**
     * Ottieni statistiche chat
     */
    fun getChatStats(): String {
        val sent = getMessageCountByType(ChatMessage.MessageType.SENT)
        val received = getMessageCountByType(ChatMessage.MessageType.RECEIVED)
        val system = getMessageCountByType(ChatMessage.MessageType.SYSTEM)

        return "Inviati: $sent | Ricevuti: $received | Sistema: $system"
    }
}