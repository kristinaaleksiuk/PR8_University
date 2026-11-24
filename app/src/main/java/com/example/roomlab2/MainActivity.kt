package com.example.roomlab2

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.roomlab2.console.Console
import com.example.roomlab2.console.Script
import com.example.roomlab2.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val pad = (12 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.root)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad)
            insets
        }
        val btnRun = findViewById<Button>(R.id.btnRun)
        val tvOutput = findViewById<TextView>(R.id.tvOutput)
        val scroll = findViewById<ScrollView>(R.id.scroll)

        val db = AppDatabase.get(this)

        btnRun.setOnClickListener {
            btnRun.isEnabled = false
            tvOutput.text = ""
            lifecycleScope.launch(Dispatchers.IO) {
                // Вывод идёт в Logcat (тег ROOM_LAB) и дублируется на экран
                Console(db) { line ->
                    runOnUiThread {
                        tvOutput.append(line + "\n")
                        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }.run(Script.commands)
                withContext(Dispatchers.Main) { btnRun.isEnabled = true }
            }
        }
    }
}
