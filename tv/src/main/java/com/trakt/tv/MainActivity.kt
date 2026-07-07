package com.trakt.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.trakt.tv.ui.TraktApp
import com.trakt.tv.ui.theme.TraktTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TraktTvApp).container
        setContent {
            TraktTvTheme {
                TraktApp(container)
            }
        }
    }
}
