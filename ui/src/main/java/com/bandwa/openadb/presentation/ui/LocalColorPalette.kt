package com.bandwa.openadb.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalColorPalette = staticCompositionLocalOf {
    Color(
        colorBrand = Color.Unspecified,
        backgroundColor = Color.Unspecified,
        tabBackgroundColor = Color.Unspecified,
        textColorPrimary = Color.Unspecified,
        textColorSecondary = Color.Unspecified,
        textColorAccent = Color.Unspecified,
        textColorSuccess = Color.Unspecified,
        textColorError = Color.Unspecified,
        iconColorPrimary = Color.Unspecified,
        glassBackgroundColor = Color.Unspecified,
        glassBorderColor = Color.Unspecified
    )
}

@Composable
internal fun colorPalette() = colorPaletteOf(isLightTheme())

/**
 * 不依赖 Composition 的版本，供 `remember` 使用。
 *
 * 原来的 `colorPalette()` 是 `@Composable`（每个颜色都得问 `LocalThemeManager`），
 * 没法放进 `remember {}` 里算 —— 但正是它每次重组都新建实例，让
 * `staticCompositionLocalOf` 的订阅者整棵树重组。拆出这个纯函数后，
 * 只要明暗不变就复用同一个实例。
 */
internal fun colorPaletteOf(light: Boolean) = Color(
    colorBrand = if (light) brandLightColor else brandDarkColor,
    backgroundColor = if (light) backgroundLightColor else backgroundDarkColor,
    tabBackgroundColor = if (light) tabBgLightColor else tabBgDarkColor,
    textColorPrimary = if (light) neutral_900 else neutral_025,
    textColorSecondary = if (light) neutral_600 else neutral_300,
    textColorAccent = if (light) accent_900 else accent_025,
    textColorError = if (light) error_600 else error_400,
    textColorSuccess = if (light) success_600 else success_400,
    iconColorPrimary = if (light) accent_900 else neutral_050,
    glassBackgroundColor = if (light) ios_glass_light else ios_glass_dark,
    glassBorderColor = if (light) ios_glass_border_light else ios_glass_border_dark
)
