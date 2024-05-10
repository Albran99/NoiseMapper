package it.dii.unipi.masss.noisemapper

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import it.dii.unipi.masss.noisemapper.ui.theme.NoiseMapperTheme
import com.google.android.material.datepicker.MaterialDatePicker

import androidx.core.util.Pair

class MainActivity : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val button: Button = findViewById(R.id.button)
        val buttonNoiseMap: Button = findViewById(R.id.button_noise_map)
        val listener = View.OnClickListener { view ->
            when (view.id) {
                R.id.button -> {
                    val intent = Intent(this, NoiseDetection::class.java)
                    startActivity(intent)
                }
                R.id.button_noise_map -> {
                    Log.i("MainActivity", "Noise map button clicked")
                    val intent = Intent(this, NoiseMap::class.java)
                    startActivity(intent)
                }
            }
        }
        button.setOnClickListener(listener)
        buttonNoiseMap.setOnClickListener(listener)
        val pickDateButton: Button = findViewById(R.id.pick_range)
        pickDateButton.setOnClickListener {
            val dateRangePicker =
                MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("Select dates")
                    .setSelection(
                        Pair(
                            MaterialDatePicker.thisMonthInUtcMilliseconds(),
                            MaterialDatePicker.todayInUtcMilliseconds()
                        )
                    )
                    .build()
            dateRangePicker.addOnPositiveButtonClickListener { selection ->
                // selection is a Pair<Long, Long> representing the selected range
                val startDate = selection.first
                val endDate = selection.second
                Log.i("MainActivity", "Date range selected: $startDate - $endDate")
            }
            dateRangePicker.show(supportFragmentManager,"dateRangePicker")
        }

    }


}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NoiseMapperTheme {
        Greeting("Android")
    }
}
