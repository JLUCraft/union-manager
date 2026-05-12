package com.jlucraft.console.domain.auth

import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.withAuthenticatedOperation
import com.jlucraft.console.data.auth.withAuthenticatedOperationUsingDeviceKey
import com.jlucraft.console.data.model.AuthPayload


 *
 *
class AuthenticatedOperationUseCase(
    private val authCoordinator: AuthCoordinator
) {


     *
     *
     *
    suspend fun <T> execute(
        cmdType: String,
        payload: AuthPayload,
        title: String,
        subtitle: String,
        operation: suspend () -> Result<T>,
        failureMessage: String
    ): Result<T> {


        val wrapped: Result<Result<T>> = authCoordinator.withAuthenticatedOperation(
            cmdType = cmdType,
            payload = payload,
            title = title,
            subtitle = subtitle
        ) {
            operation()
        }


        if (wrapped.isFailure) {
            return Result.failure(wrapped.exceptionOrNull() ?: RuntimeException("Auth failure"))
        }

        val inner = wrapped.getOrThrow()

        if (inner.isFailure) {
            val cause = inner.exceptionOrNull() ?: RuntimeException("Operation failure")


            if (cause.message.isNullOrBlank()) {
                return Result.failure(RuntimeException(failureMessage, cause))
            }
            return inner
        }

        return inner
    }

    suspend fun <T> executeUsingDeviceKey(
        cmdType: String,
        payload: (devicePublicKey: String) -> AuthPayload,
        title: String,
        subtitle: String,
        operation: suspend (devicePublicKey: String) -> Result<T>,
        failureMessage: String
    ): Result<T> {
        val wrapped: Result<Result<T>> = authCoordinator.withAuthenticatedOperationUsingDeviceKey(
            cmdType = cmdType,
            payload = payload,
            title = title,
            subtitle = subtitle
        ) { devicePublicKey ->
            operation(devicePublicKey)
        }

        if (wrapped.isFailure) {
            return Result.failure(wrapped.exceptionOrNull() ?: RuntimeException("Auth failure"))
        }

        val inner = wrapped.getOrThrow()
        if (inner.isFailure) {
            val cause = inner.exceptionOrNull() ?: RuntimeException("Operation failure")
            if (cause.message.isNullOrBlank()) {
                return Result.failure(RuntimeException(failureMessage, cause))
            }
        }
        return inner
    }
}
