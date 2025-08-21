package so.kontext.ads.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.viewmodel.ext.android.viewModel
import so.kontext.ads.app.ui.theme.SdkkotlintestappTheme
import so.kontext.ads.domain.Role
import so.kontext.ads.ui.InlineAd

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            SdkkotlintestappTheme {
                ChatScreen(
                    mainViewModel = mainViewModel,
                )
            }
        }
    }
}

@Composable
fun ChatScreen(
    mainViewModel: MainViewModel,
) {
    val messages by mainViewModel.messagesFlow.collectAsStateWithLifecycle()
    var textState by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(index = messages.lastIndex)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    messages,
                    key = { it.id },
                ) {
                    Column {
                        MessageBubble(messageUi = it)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = textState,
                    onValueChange = { textState = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message") },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (textState.isNotBlank()) {
                        mainViewModel.addMessage(textState)
                    }
                }) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(messageUi: MessageRepresentableUi) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (messageUi.role == Role.User) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (messageUi.role == Role.User) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.padding(
                    start = if (messageUi.role == Role.User) 40.dp else 0.dp,
                    end = if (messageUi.role == Role.User) 0.dp else 40.dp,
                ),
            ) {
                Text(
                    text = messageUi.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        val firstConfig = messageUi.adsConfig?.firstOrNull()
        if (firstConfig != null) {
            InlineAd(
                config = firstConfig,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}
