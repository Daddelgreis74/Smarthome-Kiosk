package com.example.smarthomekiosk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smarthomekiosk.theme.SmarthomeKioskTheme

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val errorMessage = intent.getStringExtra("error_message") ?: "Unerwarteter Fehler"
        val stackTrace = intent.getStringExtra("stack_trace") ?: "Keine Details verfuegbar."

        setContent {
            SmarthomeKioskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "⚠️ Neo Kiosk Absturz-Bericht",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Die Anwendung ist auf einen Fehler gestossen. Bitte kopiere diese Meldung:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = errorMessage,
                                    color = Color(0xFFF87171),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stackTrace,
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Crash Log", stackTrace)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(this@CrashActivity, "Fehlerbericht kopiert!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                            ) {
                                Text("📋 Kopieren")
                            }

                            Button(
                                onClick = {
                                    val restartIntent = Intent(this@CrashActivity, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    }
                                    startActivity(restartIntent)
                                    finish()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("🔄 Neu starten")
                            }
                        }
                    }
                }
            }
        }
    }
}
