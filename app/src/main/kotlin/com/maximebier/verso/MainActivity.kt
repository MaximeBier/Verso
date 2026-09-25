package com.maximebier.verso

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.maximebier.verso.ui.nav.VersoNavHost
import com.maximebier.verso.ui.theme.VersoTheme

/** Activité unique. FragmentActivity : le navigateur EPUB de Readium peut être un Fragment. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VersoTheme {
                VersoNavHost()
            }
        }
    }
}
