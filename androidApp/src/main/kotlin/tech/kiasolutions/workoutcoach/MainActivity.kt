package tech.kiasolutions.workoutcoach
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import tech.kiasolutions.workoutcoach.core.BuildProbe
import tech.kiasolutions.workoutcoach.experience.CoachApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { CoachApp() } }
    }
}
