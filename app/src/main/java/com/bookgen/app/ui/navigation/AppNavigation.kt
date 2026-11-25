package com.bookgen.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.bookgen.app.ui.components.EditorialCard
import com.bookgen.app.ui.screens.library.LibraryRoute
import com.bookgen.app.ui.screens.library.LibraryViewModel
import com.bookgen.app.ui.screens.reader.ReaderRoute
import com.bookgen.app.ui.screens.reader.ReaderViewModel
import com.bookgen.app.ui.screens.wizard.WizardRoute
import com.bookgen.app.ui.screens.wizard.WizardViewModel
import com.bookgen.app.ui.screens.workspace.WorkspaceRoute
import com.bookgen.app.ui.screens.workspace.WorkspaceViewModel
import com.bookgen.app.ui.theme.BookGenMotion
import com.bookgen.app.ui.theme.BookGenRadius
import com.bookgen.app.ui.theme.rememberPrefersReducedMotion
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun BookGenApp(
    container: AppContainer,
    initialBookId: String? = null,
    modifier: Modifier = Modifier,
) {
    val prefersReducedMotion = rememberPrefersReducedMotion()
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
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(
        backNavigationBehavior = BackNavigationBehavior.PopUntilContentChange,
    )

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        },
        sceneStrategy = listDetailStrategy,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = {
            if (prefersReducedMotion) EnterTransition.None togetherWith ExitTransition.None else forwardTransition()
        },
        popTransitionSpec = {
            if (prefersReducedMotion) EnterTransition.None togetherWith ExitTransition.None else backwardTransition()
        },
        predictivePopTransitionSpec = {
            if (prefersReducedMotion) EnterTransition.None togetherWith ExitTransition.None else backwardTransition()
        },
        modifier = modifier,
        entryProvider = entryProvider {
            entry<AppRoute.LibraryRouteKey>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = {
                        LibraryDetailPlaceholder(
                            onNewBookClick = dropUnlessResumed {
                                backStack.navigateSingleTop(AppRoute.WizardRouteKey)
                            },
                        )
                    },
                ),
            ) {
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
                        backStack.navigateToRootDetail(AppRoute.WorkspaceRouteKey(bookId))
                    },
                    onOpenReader = { bookId, chapterIndex ->
                        backStack.navigateToRootDetail(
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

            entry<AppRoute.WorkspaceRouteKey>(
                metadata = ListDetailSceneStrategy.detailPane(),
            ) { key ->
                val isInMultiPane = LocalListDetailSceneScope.current != null
                val vm: WorkspaceViewModel = viewModel(
                    key = "workspace_${key.bookId}",
                ) {
                    WorkspaceViewModel(
                        bookId = key.bookId,
                        repository = container.bookRepository,
                        settingsRepository = container.settingsRepository,
                        progressBus = container.progressBus,
                        isWorkRunningFlow = container.observeGenerationRunning(key.bookId),
                        onStartWork = { targetBookId, formatOverride, targetSectionIds, customInstruction ->
                            container.startBookGeneration(
                                bookId = targetBookId,
                                targetSectionIds = targetSectionIds,
                                customInstruction = customInstruction,
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
                    showBackButton = !isInMultiPane,
                )
            }

            entry<AppRoute.ReaderRouteKey>(
                metadata = ListDetailSceneStrategy.detailPane(),
            ) { key ->
                val isInMultiPane = LocalListDetailSceneScope.current != null
                val cameFromWorkspace = backStack.getOrNull(backStack.lastIndex - 1) is AppRoute.WorkspaceRouteKey
                val vm: ReaderViewModel = viewModel(
                    key = "reader_${key.bookId}_${key.initialChapterIndex}",
                ) {
                    ReaderViewModel(
                        bookId = key.bookId,
                        initialChapterIndex = key.initialChapterIndex,
                        exportDir = container.exportOutputDir,
                        repository = container.bookRepository,
                        settingsRepository = container.settingsRepository,
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
                            backStack.navigateToRootDetail(AppRoute.WorkspaceRouteKey(bookId))
                        }
                    },
                    showBackButton = !isInMultiPane || cameFromWorkspace,
                    allowChromeToggle = !isInMultiPane,
                )
            }
        },
    )
}

@Composable
private fun LibraryDetailPlaceholder(
    onNewBookClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            EditorialCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Co-Author Studio",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "書籍を選択して執筆・閲覧を開始",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "左側のライブラリから書籍を選ぶと、アウトライン編集・並列生成・リーダーをここに表示します。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onNewBookClick,
                        shape = BookGenRadius.Control,
                    ) {
                        Text("新しい本を企画")
                    }
                }
            }
        }
    }
}

private fun NavBackStack<NavKey>.navigateSingleTop(target: AppRoute) {
    if (lastOrNull() != target) {
        add(target)
    }
}

private fun NavBackStack<NavKey>.navigateToRootDetail(target: AppRoute) {
    if (size == 2 && lastOrNull() == target) return
    while (size > 1 && lastOrNull() !is AppRoute.WizardRouteKey) {
        removeLastOrNull()
    }
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
    val slideSpec = BookGenMotion.slideSpring()
    val slideIn = slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = slideSpec,
        initialOffset = { fullWidth -> (fullWidth * 0.16f).toInt() },
    )
    val slideOut = slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = slideSpec,
        targetOffset = { fullWidth -> -(fullWidth * 0.08f).toInt() },
    )
    return (slideIn + fadeIn(animationSpec = BookGenMotion.fadeEnterSpec()))
        .togetherWith(slideOut + fadeOut(animationSpec = BookGenMotion.fadeExitSpec()))
}

private fun <S> AnimatedContentTransitionScope<S>.backwardTransition(): ContentTransform {
    val slideSpec = BookGenMotion.slideSpring()
    val slideIn = slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = slideSpec,
        initialOffset = { fullWidth -> -(fullWidth * 0.08f).toInt() },
    )
    val slideOut = slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = slideSpec,
        targetOffset = { fullWidth -> (fullWidth * 0.16f).toInt() },
    )
    return (slideIn + fadeIn(animationSpec = BookGenMotion.fadeEnterSpec()))
        .togetherWith(slideOut + fadeOut(animationSpec = BookGenMotion.fadeExitSpec()))
}
