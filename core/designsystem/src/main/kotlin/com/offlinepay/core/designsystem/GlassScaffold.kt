package com.offlinepay.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// GlassScaffold — a Scaffold that floats on the ambient aurora background.
//
// Wraps [OfflinePayBackground] around a transparent [Scaffold], so any screen
// gets the premium glass backdrop by swapping its `Scaffold(...)` for
// `GlassScaffold(...)` — bars and content then use glass surfaces on top.
// ---------------------------------------------------------------------------

/**
 * A [Scaffold] rendered on top of the branded [OfflinePayBackground] aurora.
 *
 * The scaffold container is transparent so the aurora shows through; supply
 * glass-styled `topBar` / `bottomBar` / `floatingActionButton` slots for the
 * full effect.
 *
 * ### Usage
 * ```kotlin
 * GlassScaffold(
 *     topBar = { GlassTopBar(title = "History", onBack = onBack) },
 * ) { padding ->
 *     LazyColumn(Modifier.padding(padding)) { ... }
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: androidx.compose.foundation.layout.WindowInsets =
        ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    OfflinePayBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = LocalOfflinePayColors.current.onSurface,
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            snackbarHost = snackbarHost,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}

/**
 * Translucent [TopAppBarColors] for a glass top bar — a faint frosted panel
 * that lets the aurora bleed through while keeping title/icon legibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassTopBarColors(): TopAppBarColors {
    val glass = LocalGlassTokens.current
    val colors = LocalOfflinePayColors.current
    return TopAppBarDefaults.topAppBarColors(
        containerColor = glass.fillSubtle,
        scrolledContainerColor = glass.fill,
        titleContentColor = colors.onSurface,
        navigationIconContentColor = colors.onSurface,
        actionIconContentColor = colors.onSurface,
    )
}
