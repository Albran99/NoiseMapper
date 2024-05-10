package it.dii.unipi.masss.noisemapper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Timer
import java.util.TimerTask
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.webkit.WebView
import android.widget.DatePicker
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.kontakt.sdk.android.common.KontaktSDK
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.log10


class NoiseDetection : AppCompatActivity(), SensorEventListener {
    lateinit var bleConfig: BLEConfig
    val map_noise_level = mutableMapOf<Long , Double>()
    private val RECORD_AUDIO_BLUETOOTH_SCAN_PERMISSION_REQUEST_CODE = 101
    private val OUTPUT_FORMAT_AUDIO = MediaRecorder.OutputFormat.MPEG_4
    private val AUDIO_ENCODER = MediaRecorder.AudioEncoder.AAC
    private val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_PERFORMANCE
    private val AUDIO_ENCODING_BIT_RATE = 16*44100
    private val AUDIO_SAMPLING_RATE = 44100
    private val REFRESH_RATE = 500
    private val DB_ADJUSTMENT_PROXIMITY_SENSOR = 10
    private var mRecorder : MediaRecorder? = null
    private var timer: Timer? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var isNearObject = false
    private var ble_scanner : BLEScanner? = null
    private var pollingRequest :PollingRequest? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ble_layout)

        val webView: WebView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true;
        webView.settings.allowFileAccess = true;
        webView.settings.builtInZoomControls = true;
        //webView.webViewClient = WebViewClient()
        // I'd like to to this:
        webView.loadUrl("file://" + filesDir.absolutePath + "/output.html")

        bleConfig = BLEConfig(this.applicationContext)
        if (bleConfig.gotConfig()){ // maybe config retrieval failed
            KontaktSDK.initialize(this);  // Initialize Kontakt SDK
            // Services that cannot be started without config:
            ble_scanner = BLEScanner(this)
            pollingRequest = PollingRequest(this, bleConfig)
            // TODO: disable spinner
        } else {
            // TODO: fail
            // TODO: startActivity(Intent(this, FailActivity::class.java))
        }
        val switch : SwitchCompat = findViewById<SwitchCompat>(R.id.switch_scanning)
        switch.isChecked = false
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enterSensingState()
            } else {
                exitSensingState()

            }
        }
    }

    private fun exitSensingState() {
        switchOff()
        // Clear the view
        // TODO: update ui

    }

    private fun enterSensingState() {
        // TODO: update ui labels
        requestPermissions()
        // setup proximity sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        pollingRequest?.start()
    }

    private fun switchOff(){
        //stop the polling
        Log.i("NoiseDetection", "Switch is off")
        pollingRequest?.stop()
        if(mRecorder != null) {
            mRecorder?.stop()
            mRecorder = null
        }
        if (timer != null) {
            timer?.cancel()
            timer = null
        }
        if(ble_scanner != null){
            ble_scanner!!.stopScanning()
        }
        if(sensorManager != null){
            sensorManager!!.unregisterListener(this)
        }
    }

    private fun requestPermissions() {
        val requiredPermissions =
            arrayOf(Manifest.permission.RECORD_AUDIO) +
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                arrayOf<String>(android.Manifest.permission.ACCESS_FINE_LOCATION)
            else arrayOf<String>(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) // Note that there is no need to ask about ACCESS_FINE_LOCATION anymore for BT scanning purposes for VERSION_CODES.S and higher if we add android:usesPermissionFlags="neverForLocation" under BLUETOOTH_SCAN in your manifest file.

        // check if the permissions are already granted
        val notGrantedPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (notGrantedPermissions.isNotEmpty()) { // at least one permission has not been granted
            ActivityCompat.requestPermissions(
                this,
                notGrantedPermissions,
                RECORD_AUDIO_BLUETOOTH_SCAN_PERMISSION_REQUEST_CODE
            )
        } else {
            println("iBeacon: Permissions already granted, starting scanning")
            startSensing() // TODO: there may be a bug here: it may be possible to start sensing without checking if the bt adapter is enabled. But what should the logical flow look like?
        }
        // check that bluetooth is enabled, if not, ask the user to enable it
        val bluetoothAdapter = android.bluetooth.BluetoothManager::class.java.cast(
            getSystemService(android.content.Context.BLUETOOTH_SERVICE)
        )?.adapter
        if (!(bluetoothAdapter?.isEnabled)!!) {
            val enableBtIntent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i("BluetoothRequest", "Request code is $requestCode")
        when (requestCode) {
            RECORD_AUDIO_BLUETOOTH_SCAN_PERMISSION_REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults.filter {
                        it == PackageManager.PERMISSION_DENIED
                    }.toTypedArray().isEmpty())) {
                    Log.d("PermissionRequest", "All permissions granted")
                    startSensing()
                } else {
                    Log.d("iBeacon",  "Permission not granted")
                }
            }
        }
    }

    private fun startSensing(){ // maybe
        Log.d("MicrophoneRequest", "Permission granted")
        initializeMediaRecorder()
        noise_sampling()
        ble_scanner?.startScanning()
    }
    private fun initializeMediaRecorder(){
        mRecorder = MediaRecorder()
        mRecorder!!.setAudioSource(AUDIO_SOURCE)
        mRecorder!!.setOutputFormat(OUTPUT_FORMAT_AUDIO)
        mRecorder!!.setAudioEncoder(AUDIO_ENCODER)
        mRecorder!!.setAudioEncodingBitRate(AUDIO_ENCODING_BIT_RATE);
        mRecorder!!.setAudioSamplingRate(AUDIO_SAMPLING_RATE);
        mRecorder!!.setOutputFile(FileOutputStream(File(cacheDir, "audio.mp3")).fd)
    }
    private fun noise_sampling() {
        try {
            mRecorder!!.prepare()
            mRecorder!!.start()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (timer == null) {
            // If timer is not initialized, create and start it
            timer = Timer()
            timer?.schedule(RecorderTask(mRecorder!!), 0, REFRESH_RATE.toLong())
        }
    }
    private inner class RecorderTask(private val recorder: MediaRecorder) : TimerTask() {

        override fun run() {
            runOnUiThread {
                val amplitude = recorder.maxAmplitude
                Log.i("NoiseDetection", "Recorder max amplitude is $amplitude")
                var amplitudeDb = 20 * log10(abs(if (amplitude==0) 1 else amplitude).toDouble())
                if (isNearObject) {
                    amplitudeDb += DB_ADJUSTMENT_PROXIMITY_SENSOR // TODO: calibrate this value
                    Log.i("NoiseDetection", "Proximity sensor detected an object")
                }
                val currentTimestamp = System.currentTimeMillis()
                map_noise_level[currentTimestamp] = amplitudeDb
                Log.i("NoiseDetection", "Level db is $amplitudeDb at time $currentTimestamp")
                /*
                when {
                    amplitudeDb > 80 -> { // High noise level
                        sound.setTextColor(ContextCompat.getColor(this@NoiseDetection, R.color.high_noise))
                    }
                    amplitudeDb > 60 -> { // Medium noise level
                        sound.setTextColor(ContextCompat.getColor(this@NoiseDetection, R.color.medium_noise))
                    }
                    else -> { // Low noise level
                        sound.setTextColor(ContextCompat.getColor(this@NoiseDetection, R.color.low_noise))
                    }
                }
                 */
            }
        }
    }
/*
    override fun onPause() {
        super.onPause()
        mRecorder?.stop()
        timer.cancel()
        sensorManager?.unregisterListener(this)
    }
*/
    override fun onStart() {
        super.onStart()
        // if the switch is checked, start the BLE scanning
        /*
        val switch1: Switch = findViewById(R.id.switch1)
        if (switch1.isChecked) {
            Log.i("NoiseDetection", "Switch is on")
            sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            val pollingRequest = PollingRequest(this)
            pollingRequest.start()
        }
        */
    }

    override fun onStop() {
        super.onStop()
        switchOff()
    }

    override fun onRestart() {
        super.onRestart()
        timer = Timer()
        requestPermissions()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }


    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            isNearObject = event.values[0] < (proximitySensor?.maximumRange ?: 0.0f)
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do nothing
    }

}
