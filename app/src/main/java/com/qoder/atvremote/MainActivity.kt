package com.qoder.atvremote

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.qoder.atvremote.ui.AtvRemoteRoot
import com.qoder.atvremote.ui.RemoteViewModel
import com.qoder.atvremote.ui.theme.AtvRemoteTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            AtvRemoteTheme {
                val viewModel: RemoteViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEMO, false)) {
                        viewModel.startDemo()
                    }
                }
                AtvRemoteRoot(viewModel = viewModel)
            }
        }
    }

    private companion object {
        const val EXTRA_DEMO = "demo_mode"
    }
}
