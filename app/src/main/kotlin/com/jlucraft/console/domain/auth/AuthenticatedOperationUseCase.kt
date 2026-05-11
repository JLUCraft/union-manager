package com.jlucraft.console.domain.auth

import com.jlucraft.console.data.auth.AuthCoordinator
import com.jlucraft.console.data.auth.withAuthenticatedOperation
import com.jlucraft.console.data.model.AuthPayload

/**
 * Reusable use-case that executes an operation through the full authenticated
 * challenge-response flow provided by [AuthCoordinator.withAuthenticatedOperation].
 *
 * ViewModels that currently contain private `performAuthenticatedOperation` helpers
 * can replace them with this class, which eliminates the repeated loading/error/success
 * unwinding logic and the subtle variations in error-field naming.
 *
 * @param authCoordinator injected coordinator that owns the TEE / biometric auth flow
 */
class AuthenticatedOperationUseCase(
    private val authCoordinator: AuthCoordinator
) {

    /**
     * Authenticate, then execute [operation] (which itself returns [Result]).
     *
     * [withAuthenticatedOperation] wraps the operation's return value in its own
     * [Result], producing `Result<Result<T>>`.  This method unwinds that nesting:
     *
     * - Auth failure              → `Result.failure(authError)`
     * - Auth ok, operation failed → `Result.failure(operationError)`
     * - Auth ok, operation ok     → the inner success value
     *
     * @param cmdType        command type string forwarded to the auth challenge
     * @param payload        typed [AuthPayload] that serialises to the request body
     * @param title          biometric prompt title (shown to the user)
     * @param subtitle       biometric prompt subtitle
     * @param operation      the suspend block that performs the actual API call
     *                       and returns [Result] so the caller can signal domain
     *                       failures without throwing
     * @param failureMessage human-readable fallback used by callers that map
     *                       this result to UI error text (e.g. when an exception
     *                       carries no message)
     * @return the unwrapped [Result] from [operation], or the auth failure
     */
    suspend fun <T> execute(
        cmdType: String,
        payload: AuthPayload,
        title: String,
        subtitle: String,
        operation: suspend () -> Result<T>,
        failureMessage: String
    ): Result<T> {
        // withAuthenticatedOperation<R> returns Result<R>.
        // Here R = Result<T>, so we get Result<Result<T>>.
        val wrapped: Result<Result<T>> = authCoordinator.withAuthenticatedOperation(
            cmdType = cmdType,
            payload = payload,
            title = title,
            subtitle = subtitle
        ) {
            operation()
        }

        // Outer failure → auth was rejected, or operation threw an exception.
        if (wrapped.isFailure) {
            return Result.failure(wrapped.exceptionOrNull() ?: RuntimeException("Auth failure"))
        }

        val inner = wrapped.getOrThrow()

        if (inner.isFailure) {
            val cause = inner.exceptionOrNull() ?: RuntimeException("Operation failure")
            // When the original exception carries no meaningful message the
            // caller-supplied failureMessage provides a readable fallback.
            if (cause.message.isNullOrBlank()) {
                return Result.failure(RuntimeException(failureMessage, cause))
            }
            return inner
        }

        return inner
    }
}
