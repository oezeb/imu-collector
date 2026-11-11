package com.github.oezeb.imu_collector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import androidx.core.content.FileProvider
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
    private lateinit var delayView: EditText
    private lateinit var statusView: TextView
    private lateinit var listView: ListView
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
        delayView = findViewById(R.id.delay)
        statusView = findViewById(R.id.status)
        listView = findViewById(R.id.recordings)
        listView.emptyView = findViewById(android.R.id.empty)
        refreshRecordings()

        hourView.filters = arrayOf(MinMaxFilter(0, 99))
        minView.filters = arrayOf(MinMaxFilter(0, 59))
        secView.filters = arrayOf(MinMaxFilter(0, 59))

        btnView.setOnClickListener { record() }
    }

    companion object {
        const val SAMPLING_PERIOD_US = 20_000 // 50Hz==0.02s==20_000 μs
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
        arrayOf(btnView, hourView, minView, secView, delayView).forEach { view ->
            view.isEnabled = false
        }

        val h = hourView.text.toString()
        val m = minView.text.toString()
        val s = secView.text.toString()
        val d = delayView.text.toString()

        val hh = if (h == "") 0 else h.toLong()
        val mm = if (m == "") 0 else m.toLong()
        val ss = if (s == "") 5 else s.toLong()
        val delay = if (d == "") 5 else d.toLong()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        object : CountDownTimer(delay * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                statusView.text = "Starting in ${(millisUntilFinished + 999) / 1000}s"
            }

            override fun onFinish() {
                statusView.text = "Recording..."
                startRecording((hh * 3600 + mm * 60 + ss) * 1000)
            }
        }.start()
    }

    private fun startRecording(durationMs: Long) {
        vibrate(200)
        val dir = File(recordingsDir(), getCurrentTime().replace(":", "-"))
        dir.mkdirs()
        accFile = File(dir, "acc.txt").apply { writeText("") }
        gyrFile = File(dir, "gyr.txt").apply { writeText("") }
        magFile = File(dir, "mag.txt").apply { writeText("") }
        File(dir, "sensors.json").writeText(sensorInfoJson())

        object : CountDownTimer(durationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                var t = millisUntilFinished / 1000
                val ss = t % 60; t /= 60
                val mm = t % 60; t /= 60

                hourView.setText(String.format(Locale.CHINA, "%02d", t))
                minView.setText(String.format(Locale.CHINA, "%02d", mm))
                secView.setText(String.format(Locale.CHINA, "%02d", ss))
            }

            override fun onFinish() {
                accFile = null; gyrFile = null; magFile = null
                hourView.setText("")
                minView.setText("")
                secView.setText("")
                statusView.text = ""
                arrayOf(btnView, hourView, minView, secView, delayView).forEach { view ->
                    view.isEnabled = true
                }
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                vibrate(800)
                refreshRecordings()
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(ms)
        }
    }

    private fun recordingsDir(): File =
        File(getExternalFilesDir(null), "recordings").apply { mkdirs() }

    private fun refreshRecordings() {
        val dirs = recordingsDir().listFiles { f -> f.isDirectory }
            ?.sortedByDescending { it.name } ?: emptyList()
        listView.adapter = object : ArrayAdapter<File>(this, R.layout.item_recording, R.id.rec_name, dirs) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getView(position, convertView, parent)
                val dir = getItem(position)!!
                row.findViewById<TextView>(R.id.rec_name).text = dir.name
                row.findViewById<Button>(R.id.rec_export).setOnClickListener { export(dir) }
                row.findViewById<Button>(R.id.rec_delete).setOnClickListener { confirmDelete(dir) }
                return row
            }
        }
    }

    private fun sensorInfoJson(): String {
        val sensors = JSONObject()
        mapOf("acc" to accSensor, "gyr" to gyrSensor, "mag" to magSensor).forEach { (key, s) ->
            sensors.put(key, if (s == null) JSONObject.NULL else JSONObject().apply {
                put("name", s.name)
                put("vendor", s.vendor)
                put("version", s.version)
                put("type", s.stringType)
                put("resolution", s.resolution.toDouble())
                put("maxRange", s.maximumRange.toDouble())
                put("powerMa", s.power.toDouble())
                put("minDelayUs", s.minDelay)
                put("requestedDelayUs", SAMPLING_PERIOD_US)
            })
        }
        return JSONObject().apply {
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("androidSdk", Build.VERSION.SDK_INT)
            put("sensors", sensors)
        }.toString(2)
    }

    private fun confirmDelete(dir: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.delete_confirm, dir.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                dir.deleteRecursively()
                refreshRecordings()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun export(dir: File) {
        val exports = File(cacheDir, "exports").apply { mkdirs() }
        val zip = File(exports, "${dir.name}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            dir.listFiles()?.forEach { f ->
                out.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", zip)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.export)))
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
            sensorManager.registerListener(this, sensor, SAMPLING_PERIOD_US)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}