package so.kontext.ads.domain

public sealed interface AdLoadEvent {
    public data object Filled : AdLoadEvent
    public data object NoFill : AdLoadEvent
}
