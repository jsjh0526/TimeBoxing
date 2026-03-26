package com.example.timeboxing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.timeboxing.feature.root.TimeBoxingApp
import com.example.timeboxing.ui.theme.TimeBoxingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimeBoxingTheme {
                TimeBoxingApp()
            }
        }
    }
}
