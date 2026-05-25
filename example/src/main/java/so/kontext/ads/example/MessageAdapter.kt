package so.kontext.ads.example

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import so.kontext.ads.Session
import so.kontext.ads.model.Role
import so.kontext.ads.ui.InlineAdView

/**
 * RecyclerView adapter that renders chat messages and binds an
 * [InlineAdView] for assistant messages — exactly how a publisher
 * integrates the View path (`bind(messageId, session)` in
 * `onBindViewHolder`). Mirrors the v2.0.1 example's `MessageAdapter`.
 */
class MessageAdapter(
    private val session: Session,
) : ListAdapter<ChatMessage, MessageAdapter.MessageViewHolder>(DIFF) {

    /**
     * Invoked after an ad row grows (its WebView applied the resize height),
     * so the host can re-reveal the bottom of the list. This fires once the
     * row has actually grown — the right moment to scroll, unlike the
     * `AdHeight` event which arrives before the height is applied.
     */
    var onAdResized: (() -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view, session) { onAdResized?.invoke() }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(
        itemView: View,
        private val session: Session,
        private val onAdResized: () -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageRoot: LinearLayout = itemView.findViewById(R.id.messageRoot)
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val inlineAdView: InlineAdView = itemView.findViewById(R.id.inlineAdView)

        fun bind(message: ChatMessage) {
            messageTextView.text = message.content
            messageRoot.gravity = if (message.role == Role.USER) Gravity.END else Gravity.START

            if (message.showAd) {
                inlineAdView.visibility = View.VISIBLE
                inlineAdView.onHeightChange = {
                    inlineAdView.requestLayout()
                    onAdResized()
                }
                inlineAdView.bind(messageId = message.id, session = session)
            } else {
                // Recycled or no-longer-active slot — clear it so the old ad
                // disappears (and its WebView is released).
                inlineAdView.onHeightChange = null
                inlineAdView.unbind()
                inlineAdView.visibility = View.GONE
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem == newItem
        }
    }
}
