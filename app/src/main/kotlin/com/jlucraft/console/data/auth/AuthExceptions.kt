package com.jlucraft.console.data.auth


 *
sealed class AuthException(message: String) : Exception(message) {
    companion object {
        const val READ_ONLY_DEVICE_MESSAGE =
            "此设备不支持硬件级安全密钥存储（需 TEE/StrongBox），管理写操作已被禁止。如需写操作，请在支持 TEE 的设备上运行。"
    }
}


 *
class ReadOnlyDeviceException(
    reason: String = ""
) : AuthException(
    AuthException.READ_ONLY_DEVICE_MESSAGE +
        if (reason.isNotEmpty()) " 原因: $reason" else ""
) {
    companion object {

        fun fromCapability(capability: TeeCapability): ReadOnlyDeviceException {
            val reason = (capability as? TeeCapability.NoHardwareBackedKey)?.reason ?: ""
            return ReadOnlyDeviceException(reason)
        }
    }
}
