package com.pdfviewerapp.sunuy

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.pdfviewerapp.sunuy.ui.screens.SplashScreen
import com.pdfviewerapp.sunuy.ui.screens.HomeScreen
import com.pdfviewerapp.sunuy.ui.screens.PdfViewerScreen
import com.pdfviewerapp.sunuy.ui.screens.BookmarkScreen
import com.pdfviewerapp.sunuy.ui.screens.SettingsScreen

import androidx.navigation3.runtime.NavKey

@Composable
fun MainNavigation(
  startDestination: NavKey = Splash
) {
  val backStack = rememberNavBackStack(startDestination)
  val context = LocalContext.current

  NavDisplay(
    backStack = backStack,
    onBack = {
      if (backStack.size > 1) {
        backStack.removeLastOrNull()
      } else {
        (context as? Activity)?.finish()
      }
    },
    entryProvider =
      entryProvider {
        entry<Splash> {
          SplashScreen(
            onSplashFinished = {
              backStack.add(Home) // Navigate to Home
              backStack.remove(Splash) // Pop Splash
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
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<PdfViewer> { key ->
          PdfViewerScreen(
            pdfPath = key.pdfPath,
            onBack = { backStack.removeLastOrNull() },
            onNavigateToBookmarks = {
              backStack.add(Bookmarks(key.pdfPath))
            },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<Bookmarks> { key ->
          BookmarkScreen(
            pdfPath = key.pdfPath,
            onBack = { backStack.removeLastOrNull() },
            onBookmarkClick = {
              backStack.removeLastOrNull() // Go back to PDF viewer screen
            },
            modifier = Modifier.fillMaxSize()
          )
        }
      },
  )
}
