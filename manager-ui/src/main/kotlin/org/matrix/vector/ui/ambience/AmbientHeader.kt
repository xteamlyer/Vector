package org.matrix.vector.ui.ambience

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A header pane with a living [AmbientSurface] behind its content.
 *
 * The shared way to give any screen's header the animated background Vector's status header has:
 * the ambient canvas fills the pane and the [content] draws on top of it, so the open space around
 * the header's text responds to touch while the controls keep working. A host with no persistence
 * for the surface's scale/speed/variant simply lets [settings] fall back to the ephemeral default.
 *
 * The surface is sized with `matchParentSize`, never `fillMaxSize`: a child that fills its maximum
 * constraint would drag the pane to the whole window's height. So the pane is exactly as tall as its
 * [content], and the ambience takes whatever that is.
 */
@Composable
fun AmbientHeader(
    kind: AmbienceKind,
    tint: Color,
    modifier: Modifier = Modifier,
    settings: AmbienceSettings = AmbienceSettings.Ephemeral,
    // Square at the top so it meets the screen edge, rounded at the bottom so it reads as one pane
    // hanging from it — the shape Vector's status header uses.
    shape: Shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
    containerColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (containerColor == Color.Unspecified) Modifier
                    else Modifier.background(containerColor)
                )
    ) {
        if (kind != AmbienceKind.None) {
            AmbientSurface(
                kind = kind,
                tint = tint,
                modifier = Modifier.matchParentSize(),
                settings = settings,
            )
        }
        content()
    }
}
