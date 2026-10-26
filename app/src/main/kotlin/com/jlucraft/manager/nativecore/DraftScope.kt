package com.jlucraft.manager.nativecore

import java.security.MessageDigest

/**
 * A key of the gate-external holder that keeps a business page's public draft fields and step.
 * The business, the exact business target, the signed-in device and the identity configuration
 * never share a draft, so switching activity, server or identity never restores somebody else's
 * form.
 */
internal fun businessDraftScope(device: String, configuration: String, target: ConnectionTarget): String =
    scopedDraftKey(
        listOf(
            device,
            configurationFingerprint(configuration),
            target.address,
            target.peer,
            target.genesisHash,
        ),
    )

/** The device/configuration that owns a public identity record draft; a change discards the draft. */
internal fun identityProposalScope(device: String, configuration: String): String =
    deviceConfigurationScope(device, configuration)

/** Account pages are global, so their page state belongs to the device/configuration only. */
internal fun accountPageScope(device: String, configuration: String): String =
    deviceConfigurationScope(device, configuration)

private fun deviceConfigurationScope(device: String, configuration: String): String =
    scopedDraftKey(listOf(device, configurationFingerprint(configuration)))

/**
 * The whole configuration identifies the scope. A hash code alone is not an identity: distinct
 * configurations may share it, so the fingerprint is a digest of the complete value.
 */
private fun configurationFingerprint(configuration: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(configuration.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

/** Length-prefixed parts so an address that itself contains a separator cannot forge another key. */
private fun scopedDraftKey(parts: List<String>): String = buildString {
    parts.forEach { part ->
        append(part.length).append(':').append(part)
    }
}
