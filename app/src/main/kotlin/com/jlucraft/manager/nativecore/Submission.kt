package com.jlucraft.manager.nativecore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * A page-scoped channel to the activity. The activity calls `committed` only after it accepted an
 * accepted snapshot inside the request scope; `failed` is called for a rejected request, an
 * authentication cancellation, or a request the activity had to drop. Sending never counts as
 * success on its own.
 */
internal class FormSender(
    private val channel: (action: JSONObject, committed: ((JSONObject) -> Unit)?, failed: ((String?) -> Unit)?) -> Unit,
) {
    fun send(
        action: JSONObject,
        committed: ((JSONObject) -> Unit)? = null,
        failed: ((String?) -> Unit)? = null,
    ) = channel(action, committed, failed)
}

/**
 * In-flight state of one page's form. Inputs stay owned by the page; this only records whether the
 * latest request is still waiting and why it failed, so a late reply from a page the user already
 * left can never navigate or clear the current one.
 */
internal class FormSubmission {
    var pending by mutableStateOf(false)
        private set
    var failure by mutableStateOf<String?>(null)
        private set

    private var generation = 0L
    private var attached = true

    /** Starts a new attempt; returns the token its reply must carry. */
    fun begin(): Long {
        generation += 1
        pending = true
        failure = null
        return generation
    }

    private fun failed(token: Long, message: String?) {
        if (token != generation) return
        pending = false
        failure = message
    }

    /** The page is gone: every later reply is stale, even if the request itself was current. */
    fun detach() {
        attached = false
        generation += 1
        pending = false
    }

    /** Drops an old failure when the page starts over, without starting a new request. */
    fun clearFailure() {
        failure = null
    }

    /**
     * Sends [action] and calls [onCommitted] only for the latest attempt while this page is still
     * attached. [onCommitted] returns true when the accepted snapshot proves this exact write;
     * returning false keeps the page and its inputs. A failure keeps every input and shows
     * [failure] in place. Sending alone never completes the page.
     */
    fun submit(sender: FormSender, action: JSONObject, onCommitted: (JSONObject) -> Boolean) {
        if (pending || !attached) return
        val token = begin()
        sender.send(
            action,
            committed = { snapshot ->
                if (token == generation && attached) {
                    pending = false
                    failure = null
                    onCommitted(snapshot)
                }
            },
            failed = { message -> failed(token, message) },
        )
    }
}

/** Remembers one page's submission and detaches it when the page leaves composition. */
@Composable
internal fun rememberFormSubmission(): FormSubmission {
    val submission = remember { FormSubmission() }
    DisposableEffect(submission) { onDispose { submission.detach() } }
    return submission
}
