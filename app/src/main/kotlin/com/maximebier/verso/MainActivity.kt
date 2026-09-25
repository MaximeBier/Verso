package com.maximebier.verso

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.fragment.app.FragmentActivity
import com.maximebier.verso.spike.SpikeApp
import org.readium.r2.navigator.epub.EpubNavigatorFragment

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Un fragment restauré par le système ne peut pas être reconstruit sans publication : fabrique factice.
        supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SpikeApp(this) } }
    }
}
