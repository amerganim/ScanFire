package com.ganim.scanfire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ganim.scanfire.ui.ScanFireRoot
import com.ganim.scanfire.ui.theme.ScanFireTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ScanFireTheme {
                ScanFireRoot()
            }
        }
    }
}
