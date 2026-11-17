package com.bandwa.openadb.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LocalFontScale

/**
 * 玻璃质感文字按钮。
 *
 * [progress] 非空时按钮自带下载进度：左半部分按进度填充品牌色，**文字也跟着进度染色**
 * （底色文字 + 裁剪到进度处的白色文字两层叠加，所以文字始终居中、不跳动）。
 * 进度层全部走 `matchParentSize` 与 `drawBehind`，**不参与测量**，
 * 因此对现有几百个调用点的尺寸与布局零影响。
 */
@Composable
fun TextButton(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean = false,
    progress: Float? = null,
    onclickListener: () -> Unit
) {
    val fontScale = LocalFontScale.current

    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "alpha"
    )

    val isLight = AppTheme.colors.textColorPrimary == Color(0xFF18191A)
    val progressValue = progress?.coerceIn(0f, 1f)
    // drawBehind 里不是 Composable 上下文，颜色必须先取出来。
    val brandColor = AppTheme.colors.colorBrand

    val textColor = when {
        // 进度态：底色文字在白字叠层下面，浅色主题用品牌色、深色主题用白色
        progressValue != null -> if (isLight) AppTheme.colors.colorBrand else Color.White
        selected -> Color.White
        isLight -> AppTheme.colors.colorBrand
        else -> Color.White.copy(alpha = 0.9f)
    }

    val backgroundBrush = when {
        progressValue != null -> Brush.linearGradient(
            colors = listOf(
                AppTheme.colors.colorBrand.copy(alpha = 0.22f),
                AppTheme.colors.colorBrand.copy(alpha = 0.12f)
            )
        )
        selected -> Brush.linearGradient(
            colors = listOf(
                AppTheme.colors.colorBrand,
                AppTheme.colors.colorBrand.copy(alpha = 0.8f)
            )
        )
        isLight -> Brush.linearGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.06f),
                Color.Black.copy(alpha = 0.03f)
            )
        )
        else -> Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.2f),
                Color.White.copy(alpha = 0.1f)
            )
        )
    }

    Box(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }

            .pointerInput(onclickListener) {
                detectTapGestures(
                    onPress = {
                        try {
                            isPressed = true
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = { onclickListener() }
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .background(brush = backgroundBrush)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isLight) {
                        listOf(
                            Color.Black.copy(alpha = 0.05f),
                            Color.Black.copy(alpha = 0.1f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 28.dp),
            text = text,
            color = textColor,
            style = AppTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (0.5 * fontScale).sp
            )
        )

        if (progressValue != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    brandColor,
                                    brandColor.copy(alpha = 0.82f)
                                )
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width * progressValue, size.height)
                        )
                    }
                    .drawWithContent {
                        clipRect(right = size.width * progressValue) {
                            this@drawWithContent.drawContent()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    text = text,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = AppTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (0.5 * fontScale).sp
                    )
                )
            }
        }
    }
}
