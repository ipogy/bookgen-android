package com.bookgen.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bookgen.app.core.di.AppContainer
import com.bookgen.app.ui.screens.library.LibraryRoute
import com.bookgen.app.ui.screens.library.LibraryViewModel
import com.bookgen.app.ui.screens.reader.ReaderRoute
import com.bookgen.app.ui.screens.reader.ReaderViewModel
import com.bookgen.app.ui.screens.wizard.WizardRoute
import com.bookgen.app.ui.screens.wizard.WizardViewModel
import com.bookgen.app.ui.screens.workspace.WorkspaceRoute
import com.bookgen.app.ui.screens.workspace.WorkspaceViewModel
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination keys for the 4 core screens in BookGen.
 */
@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object LibraryRouteKey : AppRoute

    @Serializable
    data object WizardRouteKey : AppRoute

    @Serializable
    data class WorkspaceRouteKey(val bookId: String) : AppRoute

    @Serializable
    data class ReaderRouteKey(
        val bookId: String,
        val initialChapterIndex: Int = 0,
    ) : AppRoute
}

private val SlideSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private val FadeSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

@Composable
fun BookGenApp(
    container: AppContainer,
    initialBookId: String? = null,
    modifier: Modifier = Modifier,
) {
    val startDestination: AppRoute = if (!initialBookId.isNullOrBlank()) {
        AppRoute.WorkspaceRouteKey(initialBookId)
    } else {
        AppRoute.LibraryRouteKey
    }
    val backStack: NavBackStack<NavKey> = if (startDestination is AppRoute.WorkspaceRouteKey) {
        rememberNavBackStack(AppRoute.LibraryRouteKey, startDestination)
    } else {
        rememberNavBackStack(AppRoute.LibraryRouteKey)
    }

    val themePreference by container.themePreference.collectAsStateWithLifecycle()

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { forwardTransition() },
        popTransitionSpec = { backwardTransition() },
        predictivePopTransitionSpec = { backwardTransition() },
        modifier = modifier,
        entryProvider = entryProvider {
            entry<AppRoute.LibraryRouteKey> {
                val vm: LibraryViewModel = viewModel(
                    key = "library",
                ) {
                    LibraryViewModel(
                        repository = container.bookRepository,
                        settingsRepository = container.settingsRepository,
                        themePreference = container.themePreference,
                        onThemeChange = container::setThemePreference,
                    )
                }
                LibraryRoute(
                    viewModel = vm,
                    onNewBookClick = dropUnlessResumed {
                        backStack.navigateSingleTop(AppRoute.WizardRouteKey)
                    },
                    onOpenWorkspace = { bookId ->
                        backStack.navigateSingleTop(AppRoute.WorkspaceRouteKey(bookId))
                    },
                    onOpenReader = { bookId, chapterIndex ->
                        backStack.navigateSingleTop(
                            AppRoute.ReaderRouteKey(
                                bookId = bookId,
                                initialChapterIndex = chapterIndex,
                            ),
                        )
                    },
                )
            }

            entry<AppRoute.WizardRouteKey> {
                val vm: WizardViewModel = viewModel(
                    key = "wizard",
                ) {
                    WizardViewModel(
                        repository = container.bookRepository,
                        settingsRepository = container.settingsRepository,
                        generationPipeline = container.generationPipeline,
                    )
                }
                WizardRoute(
                    viewModel = vm,
                    onBack = dropUnlessResumed {
                        if (backStack.size > 1) backStack.removeLastOrNull()
                    },
                    onBookCreated = { bookId ->
                        backStack.replaceTopWith(AppRoute.WorkspaceRouteKey(bookId))
                    },
                )
            }

            entry<AppRoute.WorkspaceRouteKey> { key ->
                val vm: WorkspaceViewModel = viewModel(
                    key = "workspace_${key.bookId}",
                ) {
                    WorkspaceViewModel(
                        bookId = key.bookId,
                        repository = container.bookRepository,
                        settingsRepository = container.settingsRepository,
                        progressBus = container.progressBus,
                        isWorkRunningFlow = container.observeGenerationRunning(key.bookId),
                        onStartWork = { targetBookId, formatOverride ->
                            container.startBookGeneration(
                                bookId = targetBookId,
                                markupFormatOverride = formatOverride,
                            )
                        },
                        onCancelWork = container::cancelBookGeneration,
                    )
                }
                WorkspaceRoute(
                    viewModel = vm,
                    onBack = dropUnlessResumed {
                        if (backStack.size > 1) backStack.removeLastOrNull()
                    },
                    onOpenReader = dropUnlessResumed {
                        backStack.navigateSingleTop(
                            AppRoute.ReaderRouteKey(bookId = key.bookId, initialChapterIndex = 0),
                        )
                    },
                )
            }

            entry<AppRoute.ReaderRouteKey> { key ->
                val vm: ReaderViewModel = viewModel(
                    key = "reader_${key.bookId}_${key.initialChapterIndex}",
                ) {
                    ReaderViewModel(
                        bookId = key.bookId,
                        initialChapterIndex = key.initialChapterIndex,
                        exportDir = container.exportOutputDir,
                        repository = container.bookRepository,
                        initialTheme = themePreference,
                    )
                }
                ReaderRoute(
                    viewModel = vm,
                    onBack = dropUnlessResumed {
                        if (backStack.size > 1) backStack.removeLastOrNull()
                    },
                    onOpenWorkspace = { bookId ->
                        val existingIndex = backStack.indexOfLast {
                            it is AppRoute.WorkspaceRouteKey && it.bookId == bookId
                        }
                        if (existingIndex >= 0) {
                            while (backStack.lastIndex > existingIndex) {
                                backStack.removeLastOrNull()
                            }
                        } else {
                            backStack.navigateSingleTop(AppRoute.WorkspaceRouteKey(bookId))
                        }
                    },
                )
            }
        },
    )
}

private fun NavBackStack<NavKey>.navigateSingleTop(target: AppRoute) {
    if (lastOrNull() != target) {
        add(target)
    }
}

private fun NavBackStack<NavKey>.replaceTopWith(target: AppRoute) {
    if (size > 1) {
        removeLastOrNull()
    }
    if (lastOrNull() != target) {
        add(target)
    }
}

private fun <S> AnimatedContentTransitionScope<S>.forwardTransition(): ContentTransform {
    val slideIn = slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = SlideSpring,
        initialOffset = { fullWidth -> (fullWidth * 0.16f).toInt() },
    )
    val slideOut = slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = SlideSpring,
        targetOffset = { fullWidth -> -(fullWidth * 0.08f).toInt() },
    )
    return (slideIn + fadeIn(animationSpec = FadeSpring))
        .togetherWith(slideOut + fadeOut(animationSpec = FadeSpring))
}

private fun <S> AnimatedContentTransitionScope<S>.backwardTransition(): ContentTransform {
    val slideIn = slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = SlideSpring,
        initialOffset = { fullWidth -> -(fullWidth * 0.08f).toInt() },
    )
    val slideOut = slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = SlideSpring,
        targetOffset = { fullWidth -> (fullWidth * 0.16f).toInt() },
    )
    return (slideIn + fadeIn(animationSpec = FadeSpring))
        .togetherWith(slideOut + fadeOut(animationSpec = FadeSpring))
}
