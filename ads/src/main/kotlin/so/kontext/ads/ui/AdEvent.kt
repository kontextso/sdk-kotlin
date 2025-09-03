package so.kontext.ads.ui

public sealed interface AdEvent {
    public data object Viewed : AdEvent
    public data object Clicked : AdEvent
    public data object VideoPlayed : AdEvent
    public data object VideoClosed : AdEvent
    public data object RewardReceived : AdEvent
    public data object Generic : AdEvent
}
