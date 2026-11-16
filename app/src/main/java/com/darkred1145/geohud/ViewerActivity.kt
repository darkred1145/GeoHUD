package com.darkred1145.geohud

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil3.load
import coil3.request.crossfade

/** Built-in full-screen capture viewer; no external gallery app required. */
class ViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableHudEdgeToEdge()
        setContentView(R.layout.activity_viewer)

        findViewById<View>(R.id.header).applySystemBarPadding()
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        val uri = intent.data
        if (uri == null) {
            Toast.makeText(this, R.string.toast_no_viewer, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<ImageView>(R.id.imgViewer).load(uri) {
            crossfade(true)
        }
    }
}
