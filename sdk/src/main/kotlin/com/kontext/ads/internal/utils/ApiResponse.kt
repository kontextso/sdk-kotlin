package com.kontext.ads.internal.utils

import com.kontext.ads.internal.data.error.ApiError

internal sealed interface ApiResponse<T> {
    data class Success<T>(val data: T) : ApiResponse<T>
    data class Error<T>(val error: ApiError) : ApiResponse<T>
}
