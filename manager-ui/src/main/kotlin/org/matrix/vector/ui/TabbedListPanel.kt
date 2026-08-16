package org.matrix.vector.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

/**
 * The shared skeleton of a list screen that is split across tabs: a [PanelHeader], a row of tabs,
 * and a swipeable pager under it.
 *
 * Vector's Modules screen (a tab per Android user) and LSPatch's Manage screen (Patched apps, then
 * Modules) are the same shape — the same header, the same tab row that hides itself when there is
 * only one tab, the same pager — so they share this rather than each re-deriving it. What is left to
 * the caller is everything that differs: what the tabs are, what each page draws, and the app's own
 * `Scaffold` (snackbar host, floating action button) around it.
 *
 * The [pagerState] is created by the caller so the header can read `pagerState.currentPage` — the
 * count in the description, the floating action button that belongs to only one tab, and the
 * selection bar all follow the visible page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedListPanel(
    title: String,
    tabLabels: List<String>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    description: (@Composable () -> Unit)? = null,
    search: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    titleOverlay: (@Composable () -> Unit)? = null,
    pageContent: @Composable (page: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize()) {
        PanelHeader(
            title = title,
            actions = actions,
            description = description,
            search = search,
            titleOverlay = titleOverlay,
        )
        // Hidden on a single tab: one tab is not a choice, and the row would spend a band of the
        // screen saying so.
        if (tabLabels.size > 1) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabLabels.forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(label, fontWeight = FontWeight.Medium) },
                    )
                }
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            pageContent(page)
        }
    }
}
