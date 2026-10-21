package com.ganim.scanfire.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ganim.scanfire.R
import com.ganim.scanfire.ui.common.DarkStudio
import com.ganim.scanfire.ui.theme.Ember
import com.ganim.scanfire.ui.theme.Ink

/** Shown once, on first launch: what ScanFire does and the promise that nothing leaves the phone. */
@Composable
fun WelcomeScreen(onStart: () -> Unit) = DarkStudio {
    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(112.dp).clip(RoundedCornerShape(30.dp)),
            )
            Spacer(Modifier.height(28.dp))
            Text("Scan anything.\nKeep it yours.", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                "ScanFire turns your phone into a scanner. It's free, has no ads, and works without internet.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Point(Icons.Outlined.AutoFixHigh, "Clean, sharp pages", "Finds the edges, flattens the page and removes shadows.")
                Point(Icons.Outlined.TextFields, "Text from any page", "Copy, search, translate or listen to what's written.")
                Point(Icons.Outlined.CloudOff, "Private by design", "No account and no cloud. Your documents stay on this phone.")
            }
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White),
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 24.dp).height(58.dp),
            ) { Text("Get started", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Surface(color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(14.dp), modifier = Modifier.size(44.dp)) {
            Icon(icon, null, tint = Ember, modifier = Modifier.padding(11.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.65f))
        }
    }
}
