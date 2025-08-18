package so.kontext.ads.internal.utils

import so.kontext.ads.internal.data.error.ApiError

internal sealed interface ApiResponse<T> {
    data class Success<T>(val data: T) : ApiResponse<T>
    data class Error<T>(val error: ApiError) : ApiResponse<T>
}
