package it.dii.unipi.masss.noisemapper

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity


class NoiseMap: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.noise_map_layout)

        // Get ImageView reference

        val webView : WebView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true;
        webView.settings.allowFileAccess = true;
        webView.settings.builtInZoomControls = true;
        //webView.webViewClient = WebViewClient()
        // I'd like to to this:
        webView.loadUrl("file://" + filesDir.absolutePath + "/output.html")


        // Set the image resource (You can replace R.drawable.your_image with your actual image resource)
        val button: Button = findViewById(R.id.return_button_noise_page)
        button.setOnClickListener {
            // Create an Intent to return to the main activity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            // Close the current activity
            onStop()
        }
    }
    override fun onStop() {
        super.onStop()
    }
}
