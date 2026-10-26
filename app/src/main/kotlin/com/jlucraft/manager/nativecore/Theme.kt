package com.jlucraft.manager.nativecore

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * 全局主题。
 *
 * 色板不在这里定义：它由校徽取色生成（`BrandColors.kt`，生成器见 `brand-palette`
 * 仓库的 `bun run palette`），本文件只负责按系统模式选中对应的一套。
 */
@Composable
fun JluManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) BrandDarkColors else BrandLightColors,
        content = content,
    )
}
