package so.kontext.ads.domain

public data class Bid(
    val bidId: String,
    val code: String,
    val adDisplayPosition: AdDisplayPosition,
    val omCreativeType: OmCreativeType? = null,
    val impressionTrigger: ImpressionTrigger = ImpressionTrigger.IMMEDIATE,
)
