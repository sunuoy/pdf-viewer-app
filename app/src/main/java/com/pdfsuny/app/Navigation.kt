package com.pdfsuny.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.pdfsuny.app.ui.screens.SplashScreen
import com.pdfsuny.app.ui.screens.HomeScreen
import com.pdfsuny.app.ui.screens.PdfViewerScreen
import com.pdfsuny.app.ui.screens.BookmarkScreen
import com.pdfsuny.app.ui.screens.SettingsScreen

import androidx.navigation3.runtime.NavKey

@Composable
fun MainNavigation(
  startDestination: NavKey = Splash
) {
  val backStack = rememberNavBackStack(startDestination)
  val context = LocalContext.current

  val navigateBack: () -> Unit = {
    if (backStack.size > 1) {
      backStack.removeLastOrNull()
    } else {
      (context as? Activity)?.finish()
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = navigateBack,
    entryProvider =
      entryProvider {
        entry<Splash> {
          SplashScreen(
            onSplashFinished = {
              if (backStack.firstOrNull() == Splash) {
                backStack[0] = Home
              } else {
                backStack.add(Home)
                backStack.remove(Splash)
              }
            }
          )
        }
        entry<Home> {
          HomeScreen(
            onPdfSelected = { path ->
              backStack.add(PdfViewer(path))
            },
            onNavigateToSettings = {
              backStack.add(SettingsPage)
            },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<SettingsPage> {
          SettingsScreen(
            onBack = navigateBack,
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<PdfViewer> { key ->
          PdfViewerScreen(
            pdfPath = key.pdfPath,
            onBack = navigateBack,
            onNavigateToBookmarks = { pageIndex ->
              backStack.add(Bookmarks(key.pdfPath, pageIndex))
            },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<Bookmarks> { key ->
          BookmarkScreen(
            pdfPath = key.pdfPath,
            currentPageIndex = key.currentPageIndex,
            onBack = navigateBack,
            onBookmarkClick = {
              if (backStack.size > 1) {
                backStack.removeLastOrNull()
              }
            },
            modifier = Modifier.fillMaxSize()
          )
        }
      },
  )
}
