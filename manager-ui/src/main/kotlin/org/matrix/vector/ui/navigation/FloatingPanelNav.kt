package org.matrix.vector.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch
import org.matrix.vector.ui.R

/** Drawn size of the ball, which is also its touch target — hence not smaller than 48dp. */
private val BALL_SIZE = 52.dp

/** How much clear space is left between the parked ball and the edge it is parked against. */
private val BALL_INSET = 10.dp

/** How far from the ball a panel comes to rest once the arc is open. */
private val FAN_RADIUS = 116.dp

private val FAN_ITEM_SIZE = 48.dp

/** Wide enough for a panel name at `labelSmall`, and the width the arc is clamped by. */
private val FAN_ITEM_WIDTH = 88.dp

private val FAN_LABEL_GAP = 6.dp

/** Room kept below a fanned panel for its name, so a label cannot land off the window. */
private val FAN_LABEL_ROOM = 22.dp

/** How far apart two panels sit on the arc when the window has room for the whole fan. */
private val FAN_STEP = Math.toRadians(46.0).toFloat()

/**
 * How far off a panel's own direction the finger may point and still be pointing at it, as the
 * cosine of that angle so the test is a dot product rather than an `atan2` per panel per event.
 */
private val FAN_CONE = cos(Math.toRadians(32.0)).toFloat()

/** Within this fraction of [FAN_RADIUS] of the ball, the finger has chosen nothing. */
private const val FAN_DEAD_ZONE = 0.45f

/**
 * The panels as a ball you can put where you like, for when there is no bar at all.
 *
 * Not a `BoxScope` extension and it takes no `Alignment`: it fills the space it is given and places
 * the ball itself, because the arc has to know the window it must stay inside. It is drawn inside
 * the app window, as the last child of the shell's content Box — never a `Popup`, never a system
 * overlay: a manager running inside `com.android.shell` must not ask the shell's uid to draw over
 * every other app.
 *
 * Where the ball rests is injected via [settings], so the shared control does not reach into either
 * app's preference store; a host that does not persist it lets the ephemeral default hold it for the
 * process. [currentKey] highlights the panel the app is on, and [onSelect] hands back the whole
 * destination so the host maps its stable key to its own route.
 */
@Composable
fun FloatingPanelNav(
    panels: NavPanels,
    currentKey: String,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    settings: FloatingNavSettings = FloatingNavSettings.Ephemeral,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val rtl = layoutDirection == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    // The gesture below outlives its recompositions — see the pointerInput keys — so the callback
    // is read through a State rather than captured, or a release could call last frame's lambda.
    val select by rememberUpdatedState(onSelect)

    // Read once and written on release. The two accessors carry no flow precisely so that dragging
    // the ball does not recompose the ball, and the value is only ever authored from here.
    var atEnd by remember { mutableStateOf(settings.atEnd()) }
    var yFraction by remember { mutableFloatStateOf(settings.y()) }

    var latched by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var highlighted by remember { mutableIntStateOf(-1) }
    var fanned by remember { mutableStateOf(false) }
    val open = held || latched

    val bloom = remember { Animatable(0f) }
    val bloomSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Offset>()

    LaunchedEffect(open) {
        if (open) {
            fanned = true
            bloom.animateTo(1f, bloomSpec)
        } else {
            bloom.animateTo(0f, bloomSpec)
            fanned = false
        }
    }

    // Ahead of the nav host's own handler because this composable is the shell content's last child
    // and back callbacks fire last-registered-first: a latched arc closes before the stack moves.
    BackHandler(enabled = latched) { latched = false }

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
        val insets = WindowInsets.safeDrawing
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        val ballRadius = with(density) { BALL_SIZE.toPx() } / 2f
        val ballInset = with(density) { BALL_INSET.toPx() }
        val radius = with(density) { FAN_RADIUS.toPx() }
        val itemRadius = with(density) { FAN_ITEM_SIZE.toPx() } / 2f
        val itemHalfWidth = with(density) { FAN_ITEM_WIDTH.toPx() } / 2f
        val labelRoom = with(density) { (FAN_LABEL_GAP + FAN_LABEL_ROOM).toPx() }

        val ballBounds =
            Rect(
                left = insets.getLeft(density, layoutDirection) + ballInset + ballRadius,
                top = insets.getTop(density) + ballInset + ballRadius,
                right = width - insets.getRight(density, layoutDirection) - ballInset - ballRadius,
                bottom = height - insets.getBottom(density) - ballInset - ballRadius,
            )
        val fanBounds =
            Rect(
                left = insets.getLeft(density, layoutDirection) + itemHalfWidth,
                top = insets.getTop(density) + itemRadius,
                right = width - insets.getRight(density, layoutDirection) - itemHalfWidth,
                bottom = height - insets.getBottom(density) - itemRadius - labelRoom,
            )

        val ball =
            remember {
                Animatable(
                    resting(ballBounds, atEnd != rtl, yFraction, height),
                    Offset.VectorConverter,
                )
            }

        LaunchedEffect(ballBounds) {
            ball.snapTo(resting(ballBounds, atEnd != rtl, yFraction, height))
        }

        val visible = panels.visible
        val deadZone = radius * FAN_DEAD_ZONE

        val gesture =
            Modifier.pointerInput(
                visible,
                ballBounds,
                fanBounds,
                radius,
                width,
                height,
                layoutDirection,
            ) {
                held = false
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPress = awaitLongPressOrCancellation(down.id)

                    if (longPress != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        held = true
                        latched = false
                        highlighted = -1
                        val centre = ball.value
                        val corner = Offset(centre.x - ballRadius, centre.y - ballRadius)
                        val inward = if (atEnd != rtl) -1f else 1f
                        val points = fanPoints(visible.size, centre, inward, radius, fanBounds)
                        val released =
                            drag(down.id) { change ->
                                val hit = pick(points, centre, change.position + corner, deadZone)
                                if (hit != highlighted) {
                                    if (hit >= 0) {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.SegmentTick
                                        )
                                    }
                                    highlighted = hit
                                }
                                change.consume()
                            }
                        val chosen = highlighted
                        held = false
                        highlighted = -1
                        if (released && chosen in visible.indices) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            select(visible[chosen])
                        }
                        return@awaitEachGesture
                    }

                    val moving = currentEvent.changes.firstOrNull { it.id == down.id }?.pressed
                    if (moving != true) {
                        latched = !latched
                        haptics.performHapticFeedback(
                            if (latched) HapticFeedbackType.ContextClick
                            else HapticFeedbackType.ToggleOff
                        )
                        return@awaitEachGesture
                    }

                    latched = false
                    var target = ball.value
                    drag(down.id) { change ->
                        target = within(target + change.positionChange(), ballBounds)
                        scope.launch { ball.snapTo(target) }
                        change.consume()
                    }
                    val atRight = target.x > width / 2f
                    atEnd = atRight != rtl
                    yFraction = (target.y / height).coerceIn(0f, 1f)
                    settings.setAtEnd(atEnd)
                    settings.setY(yFraction)
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    scope.launch {
                        ball.animateTo(resting(ballBounds, atRight, yFraction, height), settleSpec)
                    }
                }
            }

        if (latched) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { latched = false }
                }
            )
        }

        if (fanned) {
            val centre = ball.value
            val inward = if (atEnd != rtl) -1f else 1f
            val points = fanPoints(visible.size, centre, inward, radius, fanBounds)
            visible.forEachIndexed { index, destination ->
                FanItem(
                    destination = destination,
                    selected = destination.key == currentKey,
                    highlighted = index == highlighted,
                    labelled = latched || index == highlighted,
                    onClick = {
                        latched = false
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        select(destination)
                    },
                    modifier =
                        Modifier.absoluteOffset {
                                val grown = bloom.value
                                val point = points[index]
                                IntOffset(
                                    (centre.x + (point.x - centre.x) * grown - itemHalfWidth)
                                        .roundToInt(),
                                    (centre.y + (point.y - centre.y) * grown - itemRadius)
                                        .roundToInt(),
                                )
                            }
                            .graphicsLayer { alpha = bloom.value },
                )
            }
        }

        val colors = MaterialTheme.colorScheme
        val ballLabel =
            stringResource(if (latched) R.string.panels_ball_close else R.string.panels_ball_open)
        Surface(
            shape = CircleShape,
            color = if (open) colors.primary else colors.primaryContainer,
            contentColor = if (open) colors.onPrimary else colors.onPrimaryContainer,
            shadowElevation = 6.dp,
            modifier =
                Modifier.absoluteOffset {
                        IntOffset(
                            (ball.value.x - ballRadius).roundToInt(),
                            (ball.value.y - ballRadius).roundToInt(),
                        )
                    }
                    .size(BALL_SIZE)
                    .semantics(mergeDescendants = true) {
                        contentDescription = ballLabel
                        role = Role.Button
                        onClick {
                            latched = !latched
                            true
                        }
                    }
                    .then(gesture),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** One panel in the open arc. */
@Composable
private fun FanItem(
    destination: TopLevelDestination,
    selected: Boolean,
    highlighted: Boolean,
    labelled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(destination.labelRes)
    val lift by animateFloatAsState(if (highlighted) 1.18f else 1f, label = "panelLift")

    Column(
        modifier = modifier.width(FAN_ITEM_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (highlighted) colors.primary else colors.surfaceContainerHigh,
            contentColor = if (highlighted) colors.onPrimary else colors.onSurfaceVariant,
            shadowElevation = if (highlighted) 8.dp else 3.dp,
            border = if (selected) BorderStroke(2.dp, colors.primary) else null,
            modifier =
                Modifier.size(FAN_ITEM_SIZE).scale(lift).semantics {
                    contentDescription = label
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    if (selected) destination.selectedIcon else destination.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (labelled) {
            Spacer(Modifier.height(FAN_LABEL_GAP))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) colors.primary else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * Where each panel's centre goes, in the coordinates of the whole overlay.
 *
 * The arc opens inward and stays inside [bounds] by construction: how far it may reach up and down
 * is turned into an angle first, the step between panels narrowed until that many fit, and the fan
 * tipped away from whichever edge is too close.
 */
private fun fanPoints(
    count: Int,
    centre: Offset,
    inward: Float,
    radius: Float,
    bounds: Rect,
): List<Offset> {
    if (count <= 0 || radius <= 0f) return emptyList()
    val up = -asin(((centre.y - bounds.top) / radius).coerceIn(0f, 1f))
    val down = asin(((bounds.bottom - centre.y) / radius).coerceIn(0f, 1f))
    val step = if (count == 1) 0f else min(FAN_STEP, (down - up) / (count - 1))
    val half = step * (count - 1) / 2f
    val tilt = within(0f, up + half, down - half)
    return List(count) { index ->
        val angle = tilt + (index - (count - 1) / 2f) * step
        Offset(
            within(centre.x + inward * radius * cos(angle), bounds.left, bounds.right),
            within(centre.y + radius * sin(angle), bounds.top, bounds.bottom),
        )
    }
}

/** Which panel the finger at [at] is pointing at, or -1 for none. */
private fun pick(points: List<Offset>, centre: Offset, at: Offset, deadZone: Float): Int {
    val reach = at - centre
    val length = reach.getDistance()
    if (length < deadZone) return -1
    var best = -1
    var closest = FAN_CONE
    points.forEachIndexed { index, point ->
        val toPanel = point - centre
        val span = toPanel.getDistance()
        if (span <= 0f) return@forEachIndexed
        val alignment = (reach.x * toPanel.x + reach.y * toPanel.y) / (length * span)
        if (alignment > closest) {
            closest = alignment
            best = index
        }
    }
    return best
}

/** Where the ball sits when nobody is holding it: against one side, [fraction] of the way down. */
private fun resting(bounds: Rect, atRight: Boolean, fraction: Float, height: Float): Offset =
    Offset(
        if (atRight) bounds.right else bounds.left,
        within(fraction * height, bounds.top, bounds.bottom),
    )

/** [point] pulled inside [bounds], which is how a dragged ball is kept in its own window. */
private fun within(point: Offset, bounds: Rect): Offset =
    Offset(within(point.x, bounds.left, bounds.right), within(point.y, bounds.top, bounds.bottom))

/** [value] pulled inside [low]..[high], tolerating the case where the two have met or crossed. */
private fun within(value: Float, low: Float, high: Float): Float =
    if (low < high) value.coerceIn(low, high) else (low + high) / 2f
