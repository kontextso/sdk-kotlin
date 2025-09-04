package so.kontext.ads.app.ui.view

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import so.kontext.ads.app.MessageRepresentableUi
import so.kontext.ads.app.R
import so.kontext.ads.domain.Role
import so.kontext.ads.ui.AdEvent
import so.kontext.ads.ui.InlineAdView

class MessageAdapter : ListAdapter<MessageRepresentableUi, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    var onAdEvent: ((event: AdEvent) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view, onAdEvent)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(
        itemView: View,
        private val onAdEvent: ((event: AdEvent) -> Unit)?,
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageRoot: LinearLayout = itemView.findViewById(R.id.messageRoot)
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val inlineAdView: InlineAdView = itemView.findViewById(R.id.inlineAdView)

        fun bind(message: MessageRepresentableUi) {
            messageTextView.text = message.text

            if (message.role == Role.User) {
                messageRoot.gravity = Gravity.END
            } else {
                messageRoot.gravity = Gravity.START
            }

            val adConfig = message.adsConfig?.firstOrNull()
            if (adConfig != null) {
                inlineAdView.visibility = View.VISIBLE
                inlineAdView.setConfig(adConfig)
                inlineAdView.onAdEventListener = { event ->
                    onAdEvent?.invoke(event)
                }
            } else {
                inlineAdView.visibility = View.GONE
                inlineAdView.setConfig(null)
            }
        }
    }
}

class MessageDiffCallback : DiffUtil.ItemCallback<MessageRepresentableUi>() {
    override fun areItemsTheSame(oldItem: MessageRepresentableUi, newItem: MessageRepresentableUi): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: MessageRepresentableUi, newItem: MessageRepresentableUi): Boolean {
        return oldItem == newItem
    }
}
