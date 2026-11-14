package com.oppo.checkin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oppo.checkin.data.CheckinResultStatus
import com.oppo.checkin.data.CheckinState

enum class GlassElevation {
    PRIMARY_ISLAND,
    SECONDARY_ISLAND,
    CAPSULE_CONTROL
}

/**
 * Ambient deep-space background with soft blurred radial light orbs.
 * Zero continuous heavy shader loops; battery-friendly static canvas composition.
 */
@Composable
fun AmbientGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.deepSpaceBase)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush. radialGradient(
                    colors = listOf(palette.ambientVioletOrb, Color.Transparent),
                    center = Offset(w * 0.22f, h * 0.16f),
                    radius = w * 0.78f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(palette.ambientBlueOrb, Color.Transparent),
                    center = Offset(w * 0.82f, h * 0.42f),
                    radius = w * 0.72f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(palette.ambientVioletOrb.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(w * 0.45f, h * 0.88f),
                    radius = w * 0.85f
                )
            )
        }
        content()
    }
}

/**
 * Reusable Layered Liquid Glass surface with translucent gradient fill, subtle top edge highlight,
 * and soft elevation shadow.
 */
@Composable
fun FloatingGlassIsland(
    modifier: Modifier = Modifier,
    elevation: GlassElevation = GlassElevation.SECONDARY_ISLAND,
    cornerRadius: Dp = 28.dp,
    accentGlow: Color? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalLiquidGlassPalette.current
    val shape = RoundedCornerShape(cornerRadius)

    val topFill = when (elevation) {
        GlassElevation.PRIMARY_ISLAND -> palette.primaryGlassHighlight
        GlassElevation.SECONDARY_ISLAND -> palette.primaryGlassSurface
        GlassElevation.CAPSULE_CONTROL -> palette.capsuleSurface
    }
    val bottomFill = when (elevation) {
        GlassElevation.PRIMARY_ISLAND -> palette.primaryGlassSurface
        GlassElevation.SECONDARY_ISLAND -> palette.secondaryGlassSurface
        GlassElevation.CAPSULE_CONTROL -> palette.secondaryGlassSurface
    }
    val borderTop = accentGlow?.copy(alpha = 0.55f) ?: when (elevation) {
        GlassElevation.PRIMARY_ISLAND -> palette.primaryGlassBorder
        GlassElevation.SECONDARY_ISLAND -> palette.secondaryGlassBorder
        GlassElevation.CAPSULE_CONTROL -> palette.capsuleBorder
    }
    val borderBottom = palette.secondaryGlassBorder.copy(alpha = 0.08f)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.97f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "islandScale"
    )

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (elevation == GlassElevation.PRIMARY_ISLAND) 18.dp else 8.dp,
                shape = shape,
                ambientColor = accentGlow ?: Color.Black.copy(alpha = 0.35f),
                spotColor = accentGlow ?: Color.Black.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(topFill, bottomFill)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(borderTop, borderBottom)
                ),
                shape = shape
            )
            .then(clickModifier)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Compact capsule glass button for quick controls ("立即测试", "执行记录", "任务设置").
 */
@Composable
fun CapsuleGlassButton(
    label: String,
    icon: ImageVector? = null,
    symbol: String? = null,
    accentColor: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidGlassPalette.current
    FloatingGlassIsland(
        modifier = modifier,
        elevation = GlassElevation.CAPSULE_CONTROL,
        cornerRadius = 999.dp,
        accentGlow = accentColor,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor ?: palette.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (symbol != null) {
                Text(
                    text = symbol,
                    color = accentColor ?: palette.accentBlueViolet,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.textPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * In-app Dynamic Execution Island that smoothly expands when automation is actively running.
 * Does NOT require SYSTEM_ALERT_WINDOW permission.
 */
@Composable
fun DynamicExecutionIsland(
    currentState: CheckinState,
    statusDetail: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidGlassPalette.current
    val isRunning = currentState != CheckinState.IDLE
    val cornerDp by animateDpAsState(
        targetValue = if (isRunning) 26.dp else 999.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dynamicIslandCorner"
    )

    AnimatedVisibility(
        visible = isRunning,
        enter = fadeIn(tween(260)) + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(tween(220)) + shrinkVertically(tween(260)),
        modifier = modifier
    ) {
        val transition = rememberInfiniteTransition(label = "orbPulse")
        val orbAlpha by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orbAlpha"
        )

        FloatingGlassIsland(
            modifier = Modifier.fillMaxWidth(),
            elevation = GlassElevation.PRIMARY_ISLAND,
            cornerRadius = cornerDp,
            accentGlow = palette.accentBlueViolet,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(palette.accentBlueViolet.copy(alpha = orbAlpha))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "◉  正在执行 OPPO 签到",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    AnimatedContent(
                        targetState = statusDetail.ifBlank { "正在启动 OPPO 商城…" },
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                        },
                        label = "dynamicIslandDetail"
                    ) { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calm vertical automation progress timeline observing the existing CheckinStateMachine.
 */
@Composable
fun AutomationTimelineIsland(
    currentState: CheckinState,
    todayStatus: CheckinResultStatus,
    modifier: Modifier = Modifier
) {
    val palette = LocalLiquidGlassPalette.current
    val activeStepIndex = resolveTimelineStepIndex(currentState, todayStatus)
    val steps = listOf(
        "启动 OPPO 商城",
        "进入会员中心",
        "查找我的积分",
        "读取签到按钮状态",
        "验证今日签到结果"
    )

    FloatingGlassIsland(
        modifier = modifier.fillMaxWidth(),
        elevation = GlassElevation.SECONDARY_ISLAND,
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "自动化流程轨迹",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textMuted
            )
            steps.forEachIndexed { index, title ->
                val isCompleted = activeStepIndex > index ||
                    (currentState == CheckinState.IDLE &&
                        (todayStatus == CheckinResultStatus.SUCCESS ||
                            todayStatus == CheckinResultStatus.ALREADY_COMPLETED))
                val isCurrent = currentState != CheckinState.IDLE && activeStepIndex == index

                val indicator = when {
                    isCompleted -> "✓"
                    isCurrent -> "●"
                    else -> "○"
                }
                val itemColor = when {
                    isCurrent -> palette.accentBlueViolet
                    isCompleted -> palette.calmMintSuccess.copy(alpha = 0.88f)
                    else -> palette.textMuted.copy(alpha = 0.65f)
                }
                val textColor = when {
                    isCurrent -> palette.textPrimary
                    isCompleted -> palette.textSecondary
                    else -> palette.textMuted.copy(alpha = 0.65f)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = indicator,
                        color = itemColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isCurrent) "正在$title" else title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun resolveTimelineStepIndex(
    state: CheckinState,
    todayStatus: CheckinResultStatus
): Int {
    return when (state) {
        CheckinState.STARTING,
        CheckinState.LAUNCHING_OPPO_MALL -> 0

        CheckinState.FINDING_MEMBERSHIP_TAB,
        CheckinState.VERIFYING_MEMBERSHIP_PAGE -> 1

        CheckinState.FINDING_MY_POINTS,
        CheckinState.VERIFYING_POINTS_PAGE -> 2

        CheckinState.LOCATING_DAILY_CHECK_IN_CARD,
        CheckinState.DETECTING_CHECK_IN_BUTTON,
        CheckinState.TODAY_NOT_CHECKED_IN,
        CheckinState.CLICKING_CHECK_IN_ONCE -> 3

        CheckinState.VERIFYING_CHECK_IN_RESULT -> 4

        CheckinState.CHECK_IN_SUCCESS,
        CheckinState.CHECK_IN_SUCCESS_WITH_LIMITED_VERIFICATION,
        CheckinState.TODAY_ALREADY_CHECKED_IN,
        CheckinState.CLEANUP_AND_RETURN -> 5

        CheckinState.IDLE -> if (
            todayStatus == CheckinResultStatus.SUCCESS ||
            todayStatus == CheckinResultStatus.ALREADY_COMPLETED
        ) 5 else -1

        else -> 3
    }
}
