package ua.any2b.tvplayer

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity

class AniTubeHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
