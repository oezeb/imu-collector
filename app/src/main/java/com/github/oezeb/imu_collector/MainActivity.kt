package com.github.oezeb.imu_collector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputFilter
import android.text.Spanned
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var accView: TextView
    private lateinit var gyrView: TextView
    private lateinit var magView: TextView
    private lateinit var hourView: EditText
    private lateinit var minView: EditText
    private lateinit var secView: EditText
    private lateinit var btnView: Button
    private var accSensor: Sensor? = null
    private var gyrSensor: Sensor? = null
    private var magSensor: Sensor? = null
    private var accFile: File? = null
    private var gyrFile: File? = null
    private var magFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyrSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        findViewById<TextView>(R.id.acc_name).text = accSensor?.name
        findViewById<TextView>(R.id.gyr_name).text = gyrSensor?.name
        findViewById<TextView>(R.id.mag_name).text = magSensor?.name

        accView = findViewById(R.id.acc_value)
        gyrView = findViewById(R.id.gyr_value)
        magView = findViewById(R.id.mag_value)
        hourView = findViewById(R.id.hour)
        minView = findViewById(R.id.min)
        secView = findViewById(R.id.sec)
        btnView = findViewById(R.id.record)

        hourView.filters = arrayOf(MinMaxFilter(0, 99))
        minView.filters = arrayOf(MinMaxFilter(0, 59))
        secView.filters = arrayOf(MinMaxFilter(0, 59))

        btnView.setOnClickListener { record() }
    }

    class MinMaxFilter(private val min: Int, private val max: Int) : InputFilter {
        override fun filter(
            source: CharSequence?,
            start: Int,
            end: Int,
            dest: Spanned?,
            dstart: Int,
            dend: Int,
        ): CharSequence? {
            val input = (dest.toString() + source.toString()).toIntOrNull()
            return if (input == null || input in min..max) null else ""
        }
    }

    private fun record() {
        arrayOf(btnView, hourView, minView, secView).forEach { view ->
            view.isEnabled = false
        }

        val h = hourView.text.toString()
        val m = minView.text.toString()
        val s = secView.text.toString()

        var hh = if (h == "") 0 else h.toLong()
        var mm = if (m == "") 0 else m.toLong()
        var ss = if (s == "") 5 else s.toLong()

        accFile = createFile("acc.txt")
        gyrFile = createFile("gyr.txt")
        magFile = createFile("mag.txt")

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        object : CountDownTimer((hh*3600+mm*60+ss)*1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                hh = millisUntilFinished / 1000
                ss = hh % 60; hh /= 60
                mm = hh % 60; hh /= 60

                hourView.setText(String.format(Locale.CHINA, "%02d", hh))
                minView.setText(String.format(Locale.CHINA, "%02d", mm))
                secView.setText(String.format(Locale.CHINA, "%02d", ss))
            }

            override fun onFinish() {
                accFile = null; gyrFile = null; magFile = null
                hourView.setText("")
                minView.setText("")
                secView.setText("")
                arrayOf(btnView, hourView, minView, secView).forEach { view ->
                    view.isEnabled = true
                }
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }.start()
    }

    private fun createFile(name: String): File {
        val file = File(this.externalCacheDir, getCurrentTime()+" "+name)
        file.writeText("")
        return file
    }

    private fun getCurrentTime() : String {
        val pattern = "yyyy-MM-dd HH:mm:ss.SSS"
        val sdf = SimpleDateFormat(pattern, Locale.CHINA)
        return sdf.format(Calendar.getInstance().time)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
//        Log.d("Sensor", "Accuracy changed: $accuracy")
    }

    override fun onSensorChanged(event: SensorEvent) {
        val currentTime = getCurrentTime()

        val (view, file, unit) = when (event.sensor) {
            accSensor -> Triple(accView, accFile, "m/s²")
            gyrSensor -> Triple(gyrView, gyrFile, "rad/s")
            magSensor -> Triple(magView, magFile, "μT")
            else -> return
        }

        view.text = buildString {
            append(event.values.map { Math.round(it * 10) / 10.0 }.joinToString(" $unit, "))
            append(" $unit")
        }
        file?.appendText("$currentTime,${event.values.joinToString(",")}\n")
    }

    override fun onResume() {
        super.onResume()
        arrayOf(accSensor, gyrSensor, magSensor).forEach { sensor ->
            val rate = 20_000 // 50Hz==0.02s==20_000 μs
            sensorManager.registerListener(this, sensor, rate)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}