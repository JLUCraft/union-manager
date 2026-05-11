package com.jlucraft.console.data.auth

/**
 * Strongly-typed auth exceptions thrown when a security precondition
 * cannot be satisfied before reaching the server.
 *
 * All subtypes carry an unambiguous [message] suitable for UI display
 * and audit logging; no subtype accepts raw or untyped values.
 */
sealed class AuthException(message: String) : Exception(message) {
    companion object {
        const val READ_ONLY_DEVICE_MESSAGE =
            "此设备不支持硬件级安全密钥存储（需 TEE/StrongBox），管理写操作已被禁止。如需写操作，请在支持 TEE 的设备上运行。"
    }
}

/**
 * Thrown when a write operation is attempted on a device that lacks
 * TEE/StrongBox-backed Ed25519 key storage.
 *
 * Prefer the factory [ReadOnlyDeviceException.fromCapability] when constructing
 * from a [TeeCapability] — it extracts the reason from
 * [TeeCapability.NoHardwareBackedKey] automatically.
 */
class ReadOnlyDeviceException(
    reason: String = ""
) : AuthException(
    AuthException.READ_ONLY_DEVICE_MESSAGE +
        if (reason.isNotEmpty()) " 原因: $reason" else ""
) {
    companion object {
        /** Creates a [ReadOnlyDeviceException] from the device's [TeeCapability],
         *  extracting the diagnostic reason from [TeeCapability.NoHardwareBackedKey]
         *  when present, or defaulting to an empty string when the capability is
         *  already hardware-backed. */
        fun fromCapability(capability: TeeCapability): ReadOnlyDeviceException {
            val reason = (capability as? TeeCapability.NoHardwareBackedKey)?.reason ?: ""
            return ReadOnlyDeviceException(reason)
        }
    }
}
