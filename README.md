[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Apache 2 License](https://img.shields.io/github/license/kontextso/sdk-kotlin)](https://github.com/kontextso/sdk-kotlin/blob/main/LICENSE)
[![[Check Main](https://github.com/kontextso/sdk-kotlin/actions/workflows/check_pr.yml/badge.svg)](https://github.com/kontextso/sdk-kotlin/actions/workflows/check_pr.yml)

# Kontext.so Kotlin SDK

The official Kotlin SDK for integrating Kontext.so ads into your Android application.

The Kontext Kotlin SDK provides an easy way to integrate Kontext.so ads into your Android application. It manages ad loading, placement, and errors with a minimalist API.

## Requirements
*   Min SDK version 26
*   Kotlin 1.9+
*   kotlinx-coroutines 1.8.1+

## Installation

The SDK is available on `mavenCentral()`. Ensure `mavenCentral()` is listed in your project's repository configuration.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Then, add the dependency to your app-level `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("so.kontext:ads:1.0.0") // Replace with the latest version
}
```

## Usage

### 1. Initialization

First, create an instance of `AdsProvider` using the `Builder`. This object should be scoped to a single conversation and ideally tied to a lifecycle-aware component, like a `ViewModel`.

```kotlin
import so.kontext.ads.AdsProvider

val adsProvider = AdsProvider.Builder(
    context = applicationContext, 
    publisherToken = "token", // Your unique publisher token from your account manager.
    userId = "user-uuid-123", // A unique string that should remain the same during the user’s lifetime.
    conversationId = "conversation-uuid-456", // Unique ID of the conversation, used for ad pacing.
    enabledPlacementCodes = listOf("inlineAd") // A list of placement codes that identify ad slots in your app.
)
    .variantId("variant-id") // A string provided by the publisher to identify the user cohort in order to compare A/B test groups (optional)
    .advertisingId("advertising-id") // Device-specific identifier provided by the operating systems (GAID)
    .regulatory( // Regulatory compliance object
        Regulatory(
            gdpr = null, // Flag that indicates whether or not the request is subject to GDPR regulations (0 = No, 1 = Yes, null = Unknown).
            gdprConsent = "gdpr-consent", // Transparency and Consent Framework's Consent String data structure
            coppa = null, // Flag whether the request is subject to COPPA (0 = No, 1 = Yes, null = Unknown).
            gpp = "gpp", // Global Privacy Platform (GPP) consent string
            gppSid = listOf(1, 2), // List of the section(s) of the GPP string which should be applied for this transaction
            usPrivacy = "us-privacy" // Communicates signals regarding consumer privacy under US privacy regulation under CCPA and LSPA
        )
    )
    .character( // The character object used in this conversation
        Character(
            id = UUID.randomUUID().toString(), // Unique ID of the character
            name = "John Doe", // Name of the character
            avatarUrl = "", // URL of the character’s avatar
            isNsfw = false, // Whether the character is NSFW
            greeting = "Hello", // Greeting of the character
            persona = "", // Description of the character’s personality
            tags = listOf() // Tags of the character (list of strings)
        )
    )
    .build()
```

### 2. Message Representation

To provide context for ad targeting, your app's chat messages must be represented in a way the SDK understands. You have two options:

**Option 1: Conform to `MessageRepresentable`**

You can make your existing message data class conform to the `MessageRepresentable` interface. This involves overriding the required properties to map to your class's fields.

```kotlin
import so.kontext.ads.MessageRepresentable
import so.kontext.ads.domain.Role

// Your existing chat message class
data class MyChatMessage(
    val uniqueId: String,
    val text: String,
    val author: String, // "user" or "assistant"
    val creationDate: String // e.g., ISO 8601 format
) : MessageRepresentable {

    // Map your properties to the interface requirements
    override val id: String
        get() = uniqueId

    override val role: Role
        get() = if (author == "user") Role.User else Role.Assistant

    override val content: String
        get() = text

    override val createdAt: String
        get() = creationDate
}
```

**Option 2: Use the `AdsMessage` Data Class**

If you cannot or prefer not to modify your existing data class, you can map your message objects to the `AdsMessage` type provided by the SDK. `AdsMessage` already conforms to `MessageRepresentable`.

```kotlin
import so.kontext.ads.AdsMessage
import so.kontext.ads.domain.Role

// When you update the SDK, map your list of messages
val messagesForSdk = myChatMessages.map { myMessage ->
    AdsMessage(
        id = myMessage.uniqueId,
        role = if (myMessage.author == "user") Role.User else Role.Assistant,
        content = myMessage.text,
        createdAt = myMessage.creationDate
    )
}

// Then pass this new list to the provider
adsProvider.setMessages(messagesForSdk)
```

### 3. Updating Messages and Collecting Ads

Whenever your list of messages changes, pass the new list to the `AdsProvider`.

The `adsProvider.ads` property is a `kotlinx.coroutines.flow.Flow` that emits a AdResult. AdResult can be either Error or Success with `Map<String, List<AdConfig>>`.
Where the map's key is the message ID, and the value is a list of ads to be displayed for that message.

Collect this flow from a `CoroutineScope` to receive and display ads.

### 4. Displaying Ads

Once you collected the ads Map in your ViewModel, you can use it in your Composable UI to display the ads. The `InlineAd` composable is provided for this purpose. It takes an `AdConfig` object and handles the ad rendering.
For View support use `InlineAdView`

The SDK provides callbacks for key ad lifecycle events, such as clicks or views, through the AdEvent sealed interface.
In Jetpack Compose use the onEvent lambda of the InlineAd composable.

```kotlin
InlineAd(
    config = adConfig,
    onEvent = { event ->
        when (event) {
            is AdEvent.Clicked -> Log.d("MyApp", "Ad clicked: ${event.url}")
            is AdEvent.Viewed -> Log.d("MyApp", "Ad viewed for message: ${event.messageId}")
            // Handle other events...
            else -> {}
        }
    }
)
```

In the View System set the onAdEventListener on your InlineAdView instance.

```kotlin
val inlineAdView: InlineAdView = findViewById(R.id.my_ad_view)

inlineAdView.setConfig(adConfig)
inlineAdView.onAdEventListener = { event ->
    when (event) {
        is AdEvent.Clicked -> Log.d("MyApp", "Ad clicked: ${event.url}")
        is AdEvent.Viewed -> Log.d("MyApp", "Ad viewed for message: ${event.messageId}")
        // Handle other events...
        else -> {}
    }
}
```

### 5. Lifecycle Management

It is crucial to release the resources used by the SDK. Call the `close()` method when the `AdsProvider` is no longer needed.

### Example ViewModel Setup

Here is a simplified example of how to integrate the `AdsProvider` into an Android `ViewModel`. For a complete, working implementation, please see the `example` module in this repository.

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import so.kontext.ads.AdsProvider
import so.kontext.ads.MessageRepresentable

class ChatViewModel(application: Application) : ViewModel() {

    private val adsProvider: AdsProvider
    private val _messages = MutableStateFlow<List<MyChatMessage>>(emptyList())

    init {
        adsProvider = AdsProvider.Builder(
            context = application,
            publisherToken = "...",
            userId = "...",
            conversationId = "...",
            enabledPlacementCodes = listOf("inlineAd")
        ).build()

        // Collect the flow of ads
        viewModelScope.launch {
            adsProvider.ads.collect { result ->
                when (result) {
                    is AdResult.Error -> {
                        // handle error
                    }
                    is AdResult.Success -> {
                        // Update your UI state with the new ads
                    }
                }
            }
        }
    }

    fun onNewMessage(message: MyChatMessage) {
        // Update your local message list
        val updatedMessages = _messages.value + message
        _messages.value = updatedMessages

        // Pass the updated list to the SDK
        viewModelScope.launch {
            adsProvider.setMessages(updatedMessages)
        }
    }

    override fun onCleared() {
        // IMPORTANT: Clean up SDK resources
        adsProvider.close()
        super.onCleared()
    }
}
```

## Documentation
For more information, see the documentation: https://docs.kontext.so/sdk/android

## License
This SDK is licensed under the Apache License 2.0. See the `LICENSE` file for details.
