package com.cabeceira.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cabeceira.app.ui.detail.BookDetailScreen
import com.cabeceira.app.ui.search.SearchScreen
import com.cabeceira.app.ui.settings.SettingsScreen
import com.cabeceira.app.ui.shelf.ShelfScreen
import com.cabeceira.app.ui.theme.CabeceiraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CabeceiraTheme { CabeceiraNavHost() }
        }
    }
}

private object Routes {
    const val SHELF = "shelf"
    const val SEARCH = "search?author={author}"
    const val BOOK = "book/{id}"
    const val SETTINGS = "settings"

    fun search(author: String? = null) = if (author == null) "search" else "search?author=${Uri.encode(author)}"
    fun book(id: Long) = "book/$id"
}

@androidx.compose.runtime.Composable
private fun CabeceiraNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.SHELF) {
        composable(Routes.SHELF) {
            ShelfScreen(
                onAddBook = { nav.navigate(Routes.search()) },
                onOpenBook = { nav.navigate(Routes.book(it)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.SEARCH,
            arguments = listOf(navArgument("author") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            SearchScreen(
                initialAuthor = entry.arguments?.getString("author"),
                onBack = { nav.popBackStack() },
                onBookAdded = { id ->
                    nav.navigate(Routes.book(id)) { popUpTo(Routes.SHELF) }
                },
                onOpenExisting = { id -> nav.navigate(Routes.book(id)) { popUpTo(Routes.SHELF) } },
            )
        }
        composable(
            Routes.BOOK,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            BookDetailScreen(
                bookId = id,
                onBack = { nav.popBackStack() },
                onOpenBook = { nav.navigate(Routes.book(it)) },
                onSearchAuthor = { nav.navigate(Routes.search(it)) },
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
