package so.kontext.ads.app.ui.view

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import so.kontext.ads.app.MainViewModel
import so.kontext.ads.app.R

class MainViewActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root: View = findViewById(R.id.main)
        val recyclerView: RecyclerView = findViewById(R.id.messagesRecyclerView)
        val messageInput: EditText = findViewById(R.id.messageEditText)
        val sendButton: Button = findViewById(R.id.sendButton)
        val inputLayout: LinearLayout = findViewById(R.id.inputLayout)

        val messageAdapter = MessageAdapter()
        recyclerView.adapter = messageAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            recyclerView.updatePadding(top = sysBars.top)
            inputLayout.updatePadding(bottom = maxOf(sysBars.bottom, ime.bottom))

            insets
        }

        sendButton.setOnClickListener {
            val text = messageInput.text.toString()
            if (text.isNotBlank()) {
                mainViewModel.addMessage(text)
                messageInput.text.clear()
            }
        }

        lifecycleScope.launch {
            mainViewModel.messagesFlow.collect { messages ->
                messageAdapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    recyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        }
    }
}
