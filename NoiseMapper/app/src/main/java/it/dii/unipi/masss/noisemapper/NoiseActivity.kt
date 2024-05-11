package it.dii.unipi.masss.noisemapper

import android.content.Context
import android.os.Bundle
import android.os.SystemClock.sleep
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.DatePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.datepicker.MaterialDatePicker

import androidx.core.util.Pair

class NoiseActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.noise_activity)
        Log.i("NoiseActivity", "Noise activity started")
        // call the server function in order to obtain the config file
        val bleConfig =BLEConfig(this)
        // if no connection check that there is the config file
        // if no config file, show a toast message
        if (!bleConfig.gotConfig()){
            Toast.makeText(
                this,
                "Please connect to Internet for the first app lunch",
                Toast.LENGTH_LONG
            ).show()
            finish() // go back to main activity
        }


        val pickDateButton: Button = findViewById(R.id.pick_date_button)
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

                val noice_map_io = NoiseMapIO(this);
                noice_map_io.performGetRequest(startDate,endDate)

                Log.i("MainActivity", "Date range selected: $startDate - $endDate")
            }
            dateRangePicker.show(supportFragmentManager, "dateRangePicker")
        }

        val webView : WebView = findViewById(R.id.map_web_view)
        webView.settings.javaScriptEnabled = true;
        webView.settings.allowFileAccess = true;
        //webView.webViewClient = WebViewClient()
        // I'd like to to this:
        webView.loadUrl("file://" + filesDir.absolutePath + "/output.html")
        // register for the switch compat change event
        val switchCompat: SwitchCompat = findViewById(R.id.sensing_on_off)
        switchCompat.setOnCheckedChangeListener { _, isChecked ->
            // do whatever you need to do when the switch is toggled here
            Log.i("NoiseMapper","Switch event")
        }

        }
    }

