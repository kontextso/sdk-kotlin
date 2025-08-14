package com.kontext.ads.internal.utils

import com.kontext.ads.internal.data.error.ApiError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.serialization.SerializationException
import java.io.IOException

@Suppress("TooGenericExceptionCaught")
internal inline fun <T> withApiCall(apiCall: () -> T): ApiResponse<T> {
    return try {
        val result = apiCall()
        ApiResponse.Success(result)
    } catch (exception: HttpRequestTimeoutException) {
        ApiResponse.Error(
            ApiError.Timeout(
                cause = exception,
            ),
        )
    } catch (exception: SerializationException) {
        ApiResponse.Error(
            ApiError.Serialization(
                cause = exception,
            ),
        )
    } catch (exception: ClientRequestException) {
        ApiResponse.Error(
            ApiError.Http(
                cause = exception,
                code = exception.response.status.value,
            ),
        )
    } catch (exception: ServerResponseException) {
        ApiResponse.Error(
            ApiError.Http(
                cause = exception,
                code = exception.response.status.value,
            ),
        )
    } catch (exception: IOException) {
        ApiResponse.Error(
            ApiError.Connection(
                cause = exception,
            ),
        )
    } catch (exception: Exception) {
        ApiResponse.Error(
            ApiError.UnexpectedError(
                cause = exception,
            ),
        )
    }
}
