package com.bandwa.openadb.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** 渐变描边用的两个静态 Brush：`liquidGlassSurface` 以前每次调用都新建一遍，白花 CPU。 */
private val borderBrushDark = Brush.verticalGradient(
    0.0f to Color.White.copy(alpha = 0.5f),
    0.5f to Color.White.copy(alpha = 0.15f),
    1.0f to Color.White.copy(alpha = 0.35f)
)

private val borderBrushLight = Brush.verticalGradient(
    0.0f to Color.White.copy(alpha = 0.3f),
    0.5f to Color.White.copy(alpha = 0.05f),
    1.0f to Color.White.copy(alpha = 0.15f)
)

/**
 * 玻璃表面：底色 + 渐变描边 + 裁剪。**不含阴影**。
 *
 * 阴影必须单独画在"不含任何滚动内容"的那一层（见 [LiquidContainer]）。原因：
 * `Modifier.shadow` 会把元素画进一个图形层，元素内部的任何变化（比如里面滚动的列表）
 * 都会让这个层失效并连同阴影一起重绘 —— 而这两块容器是 220dp×全高 与 约1000dp×全高，
 * 每帧重绘一次大面积阴影就是滑动掉帧的主因。
 */
fun Modifier.liquidGlassSurface(
    shape: Shape = shapes.large,
    containerColor: Color
): Modifier {
    val isLight = containerColor.red > 0.8f && containerColor.green > 0.8f

    return this

        .background(
            color = containerColor,
            shape = shape
        )

        .border(
            width = 1.dp,
            brush = if (isLight) borderBrushLight else borderBrushDark,
            shape = shape
        )

        // 裁剪必须保留：滚动内容会滚出圆角，不裁就会从直角边上露出来。
        .clip(shape)
}

/**
 * 兼容旧调用点：表面 + 阴影一起，用在一个会滚动的内容上时请改用 [GlassyContainer]。
 */
fun Modifier.liquidGlass(
    shape: Shape = shapes.large,
    containerColor: Color
): Modifier = this
    .shadow(
        elevation = 8.dp,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = if (isLightColor(containerColor)) 0.05f else 0.15f),
        spotColor = Color.Black.copy(alpha = if (isLightColor(containerColor)) 0.1f else 0.25f)
    )
    .liquidGlassSurface(shape, containerColor)

private fun isLightColor(color: Color) = color.red > 0.8f && color.green > 0.8f

@Composable
fun LiquidContainer(
    modifier: Modifier = Modifier,
    shape: Shape = shapes.large,
    backgroundColor: Color = AppTheme.colors.glassBackgroundColor,
    content: @Composable BoxScope.() -> Unit
) {
    val isLight = isLightColor(backgroundColor)

    Box(
        modifier = modifier
    ) {
        // 静态层：阴影 + 底色 + 描边，全画在这里，**这一层的尺寸虽然跟随容器，
        // 但内容是一个空 Box** ⇒ 里面的列表怎么滚都不会让它失效重绘。
        //
        // 以前底色和描边是画在"装着滚动内容"那一层上的，等于每滚动一帧就要把
        // 整屏大小的圆角渐变边框重画一遍；车机 CPU 弱，这就是滑动不跟手的直接原因。
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 8.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = if (isLight) 0.05f else 0.15f),
                    spotColor = Color.Black.copy(alpha = if (isLight) 0.1f else 0.25f)
                )
                .background(color = backgroundColor, shape = shape)
                .border(
                    width = 1.dp,
                    brush = if (isLight) borderBrushLight else borderBrushDark,
                    shape = shape
                )
        )

        // 内容层：只负责裁剪与布局，**不再画底色和描边**（那两样留在上面那一层的
        // 静态绘制里）。滚动时这一层要重录，但重录的只是内容本身，不再是整屏大小的
        // 圆角渐变边框 + 阴影。
        Box(
            modifier = Modifier.clip(shape),
            content = content
        )
    }
}

@Composable
fun GlassyContainer(
    modifier: Modifier = Modifier,
    shape: Shape = shapes.large,
    backgroundColor: Color = AppTheme.colors.glassBackgroundColor,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidContainer(modifier, shape, backgroundColor, content)
}
