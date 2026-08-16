@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
)

package org.matrix.vector.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import org.matrix.vector.ui.R

/** What is left of a hidden panel in edit mode: Material's disabled alpha, saying the same. */
private const val HIDDEN_ALPHA = 0.38f

/** How much the dragged item grows, so a finger is visibly carrying it rather than pushing it. */
private const val DRAG_LIFT = 1.08f

/**
 * The panels, as the navigation container's items.
 *
 * A plain composable, not a scope extension: the scaffold's `navigationItems` slot has no receiver,
 * and whatever this emits becomes a direct child of ShortNavigationBar or WideNavigationRail. It
 * must therefore emit its items as flat siblings — ShortNavigationBar measures every direct child
 * as one equal-width slot, so wrapping them in a Row would hand a single slot the whole bar.
 *
 * [editing] shows every panel, hidden ones dimmed, with a badge and a drag; otherwise it shows
 * [NavPanels.visible] and a long press asks for [onEdit]. Both index arguments of [onMove] are
 * indices into [NavPanels.all], which is what [editing] is showing when a drag is possible at all.
 * The selected item is decided by [currentKey], and [onSelect] hands back the whole destination so
 * the host can map its stable key to its own route type.
 */
@Composable
fun PanelBar(
    panels: NavPanels,
    currentKey: String,
    editing: Boolean,
    suiteType: NavigationSuiteType,
    onSelect: (TopLevelDestination) -> Unit,
    onEdit: () -> Unit,
    onToggleHidden: (key: String, hidden: Boolean) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val horizontal = isHorizontal(suiteType)
    val items = if (editing) panels.all else panels.visible
    // Rebuilt whenever the arrangement it describes stops being the one on screen. A drag cannot
    // outlive either change — leaving edit mode ends it, and the axis only flips on a rotation —
    // so there is nothing in flight to lose, and a stale slot table is exactly how a reorder ends
    // up dropping an item in the wrong place.
    val drag = remember(items.size, horizontal) { PanelDrag(items.size) }

    items.forEachIndexed { index, destination ->
        PanelItem(
            destination = destination,
            index = index,
            count = items.size,
            selected = destination.key == currentKey,
            editing = editing,
            hidden = panels.isHidden(destination),
            canHide = panels.canHide(destination),
            horizontal = horizontal,
            drag = drag,
            onSelect = { onSelect(destination) },
            onEdit = onEdit,
            onToggleHidden = { hidden -> onToggleHidden(destination.key, hidden) },
            onMove = onMove,
        )
    }
}

/**
 * The way out of edit mode that is not the back gesture.
 *
 * Goes in the scaffold's `primaryActionContent`, which the suite draws above a bar and as the
 * header of a rail — the one place that is present on both axes without this having to know which
 * it is on. A FloatingActionButton because that is what the slot is documented to hold; anything
 * flatter reads as one more navigation item rather than as the way out.
 */
@Composable
fun PanelEditDone(onDone: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    FloatingActionButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onDone()
        }
    ) {
        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.panels_done))
    }
}

/**
 * Whether [type] lays its items along the bottom of the window rather than down its side.
 *
 * The library's own `isNavigationBar` is private and NavigationSuiteType is a value class with no
 * `values()`, so this is re-derived by comparison against the three bar values.
 */
fun isHorizontal(type: NavigationSuiteType): Boolean =
    type == NavigationSuiteType.ShortNavigationBarCompact ||
        type == NavigationSuiteType.ShortNavigationBarMedium ||
        type == NavigationSuiteType.NavigationBar

/**
 * One panel: the container's item, and in edit mode the badge and the drag over the top of it.
 */
@Composable
private fun PanelItem(
    destination: TopLevelDestination,
    index: Int,
    count: Int,
    selected: Boolean,
    editing: Boolean,
    hidden: Boolean,
    canHide: Boolean,
    horizontal: Boolean,
    drag: PanelDrag,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onToggleHidden: (Boolean) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val name = stringResource(destination.labelRes)
    val rearrange = stringResource(R.string.settings_rearrange_panels)
    val moveEarlier = stringResource(R.string.panels_move_earlier)
    val moveLater = stringResource(R.string.panels_move_later)

    val dragged = drag.from == index
    val lift = animateFloatAsState(if (dragged) DRAG_LIFT else 1f, label = "panelLift")
    val shift = animateFloatAsState(drag.displacement(index), label = "panelShift")

    val slotGesture =
        if (editing) {
            Modifier.pointerInput(drag, index, horizontal, editing) {
                detectDragGestures(
                    onDragStart = { drag.start(index) },
                    onDragEnd = {
                        val from = drag.from
                        val to = drag.to
                        drag.cancel()
                        if (from >= 0 && from != to) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onMove(from, to)
                        }
                    },
                    onDragCancel = { drag.cancel() },
                    onDrag = { change, amount ->
                        change.consume()
                        val along = if (horizontal) amount.x else amount.y
                        if (drag.drag(along)) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    },
                )
            }
        } else {
            Modifier.pointerInput(index, editing) {
                awaitPanelLongPress {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit()
                }
            }
        }

    Box(
        modifier =
            Modifier.zIndex(if (dragged) 1f else 0f)
                .then(
                    if (!editing) Modifier
                    else
                        Modifier.onGloballyPositioned {
                            val position = it.positionInParent()
                            drag.reportSlot(index, if (horizontal) position.x else position.y)
                        }
                )
                .absoluteOffset {
                    val along = if (drag.from == index) drag.offset else shift.value
                    if (horizontal) IntOffset(along.roundToInt(), 0)
                    else IntOffset(0, along.roundToInt())
                }
                .graphicsLayer {
                    scaleX = lift.value
                    scaleY = lift.value
                }
                .then(slotGesture),
        contentAlignment = Alignment.Center,
    ) {
        val itemSemantics =
            if (editing) {
                Modifier.semantics {
                    val moves = mutableListOf<CustomAccessibilityAction>()
                    if (index > 0) {
                        moves +=
                            CustomAccessibilityAction(moveEarlier) {
                                onMove(index, index - 1)
                                true
                            }
                    }
                    if (index < count - 1) {
                        moves +=
                            CustomAccessibilityAction(moveLater) {
                                onMove(index, index + 1)
                                true
                            }
                    }
                    customActions = moves
                }
            } else {
                Modifier.semantics {
                    onLongClick(label = rearrange) {
                        onEdit()
                        true
                    }
                }
            }
        val itemModifier =
            (if (horizontal) Modifier.fillMaxSize() else Modifier)
                .graphicsLayer { alpha = if (hidden) HIDDEN_ALPHA else 1f }
                .then(itemSemantics)
        // The label doubles as the item's accessibility name, so the icon carries no
        // contentDescription of its own — otherwise TalkBack announces every selected tab twice.
        val glyph = if (selected) destination.selectedIcon else destination.icon
        val icon: @Composable () -> Unit = { Icon(glyph, contentDescription = null) }
        val label: @Composable () -> Unit = { Text(name) }
        val onClick: () -> Unit = { if (!editing) onSelect() }

        if (horizontal) {
            ShortNavigationBarItem(
                selected = selected,
                onClick = onClick,
                icon = icon,
                label = label,
                modifier = itemModifier,
            )
        } else {
            WideNavigationRailItem(
                selected = selected,
                onClick = onClick,
                icon = icon,
                label = label,
                railExpanded = false,
                modifier = itemModifier,
            )
        }

        if (editing && (hidden || canHide)) {
            PanelBadge(
                hidden = hidden,
                name = name,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onToggleHidden(!hidden)
                },
            )
        }
    }
}

/** The minus or plus at the corner of a panel being rearranged. */
@Composable
private fun BoxScope.PanelBadge(hidden: Boolean, name: String, onClick: () -> Unit) {
    val description =
        stringResource(if (hidden) R.string.panels_show else R.string.panels_hide, name)
    val container =
        if (hidden) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.errorContainer
    val content =
        if (hidden) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onErrorContainer

    Box(
        modifier =
            Modifier.align(Alignment.TopEnd)
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(18.dp),
            shape = CircleShape,
            color = container,
            contentColor = content,
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (hidden) Icons.Rounded.Add else Icons.Rounded.Remove,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/**
 * Long press to enter edit mode, resolved on the initial pointer pass.
 *
 * `combinedClickable` on the slot is the obvious way and it does not work. The item inside carries
 * its own `selectable`, which is the nearer node and therefore handles the release first, so a long
 * press on Logs would open edit mode *and* switch to Logs. Watching the pointer on
 * [PointerEventPass.Initial] gets ahead of it: nothing is consumed while the press might still turn
 * out to be a tap, and everything from the moment the press has been held long enough is consumed,
 * which cancels the click that would otherwise land on the way up.
 */
private suspend fun PointerInputScope.awaitPanelLongPress(onLongPress: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation(PointerEventPass.Initial)
            }
            return@awaitEachGesture
        } catch (_: PointerEventTimeoutCancellationException) {
            onLongPress()
        }
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) return@awaitEachGesture
        }
    }
}

/**
 * A reorder in flight, shared by every item in the container.
 *
 * The axis never appears here. [PanelBar] hands over the one number that differs between a bottom
 * bar and a rail — where each slot sits along whichever direction the container runs in — so the
 * arithmetic below is written once and both layouts get the same behaviour.
 */
@Stable
private class PanelDrag(count: Int) {

    var from by mutableIntStateOf(-1)
        private set

    var to by mutableIntStateOf(-1)
        private set

    var offset by mutableFloatStateOf(0f)
        private set

    private val slots = FloatArray(count)

    private val active: Boolean
        get() = from in slots.indices

    fun reportSlot(index: Int, position: Float) {
        if (!active && index in slots.indices) slots[index] = position
    }

    fun start(index: Int) {
        from = index
        to = index
        offset = 0f
    }

    fun drag(delta: Float): Boolean {
        if (!active) return false
        offset += delta
        val target = nearest(slots[from] + offset)
        if (target == to) return false
        to = target
        return true
    }

    fun cancel() {
        from = -1
        to = -1
        offset = 0f
    }

    fun displacement(index: Int): Float {
        if (!active || index == from) return 0f
        return when {
            index in (from + 1)..to -> slots[index - 1] - slots[index]
            index in to until from -> slots[index + 1] - slots[index]
            else -> 0f
        }
    }

    private fun nearest(position: Float): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (index in slots.indices) {
            val distance = abs(slots[index] - position)
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }
}
