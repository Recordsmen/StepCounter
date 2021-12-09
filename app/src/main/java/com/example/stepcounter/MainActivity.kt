package com.example.stepcounter

import android.Manifest
import android.R.attr
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.data.Value
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.DataSourcesRequest
import com.google.android.gms.fitness.request.OnDataPointListener
import com.google.android.gms.fitness.request.SensorRequest
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import com.google.android.gms.common.api.ApiException

import android.R.attr.data
import com.google.android.gms.tasks.Task
import java.text.SimpleDateFormat
import java.util.*


const val TAG = "StepCounter"
const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1
const val MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION = 2


class MainActivity : AppCompatActivity() {

    val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.zzmd)
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
        .build()

    private lateinit var spinner_fps: Spinner
    private lateinit var start: Button
    private lateinit var steps: TextView
    private lateinit var gyroscope: TextView
    private lateinit var accelerometer: TextView
    private lateinit var compass: TextView
    private lateinit var subject: TextView

    private var manager: SensorManager? = null

    private var floatGravity = FloatArray(3)
    private var floatGeoMagnetic = FloatArray(3)
    private var floatOrientation = FloatArray(3)
    private var floatRotationMatrix = FloatArray(9)

    var change = false

    //spinnerValue
    var fps = arrayOf("30", "40", "50", "60", "70")
    var total = 0
    var selectedFps = 60
    var frameNumber = 0
    var rotation = "0"
    var magnetometer = "0"
    lateinit var dateFormat:SimpleDateFormat
    lateinit var currentDateTime:String
    lateinit var file: File
    lateinit var writer: FileWriter
    lateinit var listener: OnDataPointListener
    lateinit var poseListener: SensorEventListener
    lateinit var account: GoogleSignInAccount

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        start = findViewById(R.id.btn_start_stop)
        spinner_fps = findViewById(R.id.spinner_fps)
        steps = findViewById(R.id.tv_steps)
        gyroscope = findViewById(R.id.tv_gyroscope)
        accelerometer = findViewById(R.id.tv_accelerometer)
        compass = findViewById(R.id.tv_compass)
        subject = findViewById(R.id.tv_subjectName)

        file = File(
            this.filesDir,
            "${subject.text}_timestamp_${selectedFps}_raw_sensor_data_and_steps.csv"
        )

        writer = FileWriter(file)
        writer.write("FrameNumber,Timestamp,GyroData,AccelerometerData,MagnetometerData,Rotation,StepCounter\n")

        manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        poseListener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

            }

            override fun onSensorChanged(event: SensorEvent?) {

                if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
                    gyroscope.text = "x: ${event?.values[0]}"
                    gyroscope.append(" y: ${event?.values[1]}")
                    gyroscope.append(" z: ${event?.values[2]}")
                }

                if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    accelerometer.text = "x:${event?.values[0]}"
                    accelerometer.append(" y:${event?.values[1]}")
                    accelerometer.append(" z:${event?.values[2]}")
                    floatGravity = event.values
                    //rotation
                    rotation= windowManager.defaultDisplay.rotation.toString()
                }

                if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    floatGeoMagnetic = event.values
                    magnetometer = "x:${floatGeoMagnetic[0]}y:${floatGeoMagnetic[1]}z:${floatGeoMagnetic[2]}"
                    SensorManager.getRotationMatrix(
                        floatRotationMatrix,
                        null,
                        floatGravity,
                        floatGeoMagnetic
                    )

                    SensorManager.getOrientation(floatRotationMatrix, floatOrientation)

                    var radians =
                        ((Math.toDegrees(floatOrientation[0].toDouble()) + 360).toFloat() % 360)

                    compass.text =
                        when (radians) {
                            in 0.0..22.0 -> "Compass: North $radians "
                            in 22.0..67.0 -> "Compass: North East $radians"
                            in 67.0..112.0 -> "Compass: East $radians"
                            in 112.0..157.0 -> "Compass: South East $radians"
                            in 157.0..202.0 -> "Compass: South $radians"
                            in 202.0..247.0 -> "Compass: South West $radians"
                            in 247.0..292.0 -> "Compass: West $radians"
                            in 292.0..337.0 -> "Compass: North West $radians"
                            in 337.0..360.0 -> "Compass: North $radians"
                            else -> "Compass: Compass nor active"
                        }
                }
            }
        }


        listener = OnDataPointListener { dataPoint ->
            for (field in dataPoint.dataType.fields) {
                val value = dataPoint.getValue(field)
                Log.i(TAG, "Detected DataPoint field: ${field.name}")
                Log.i(TAG, "Detected DataPoint value: $value")
                total += value.asInt()
                frameNumber++
                steps.text = "$total "
                //Timestamp
                dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"	)
                currentDateTime = dateFormat.format(Date())

                writer.write("${frameNumber},${currentDateTime},${gyroscope.text}" +
                        ",${accelerometer.text},${magnetometer},${rotation},${steps.text}\n")

            }
        }

        //AppRequestPermissions
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION
        )

        account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                this, // your activity
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE, // e.g. 1
                account,
                fitnessOptions
            )
        } else {
            accessGoogleFit()
        }

        //spinnerAdapter
        val adapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, fps)

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner_fps.adapter = adapter
        spinner_fps.setSelection(3)
        selectedFps = when (spinner_fps.selectedItem) {
            "30" -> 33
            "40" -> 25
            "50" -> 20
            "60" -> 16
            "70" -> 14
            else -> 0
        }

        start.setOnClickListener(View.OnClickListener {
            change = when (change) {
                false -> buttonStart()
                true -> buttonStop()
            }
        })
        Fitness.getSensorsClient(this, GoogleSignIn.getAccountForExtension(this, fitnessOptions))
            .findDataSources(
                DataSourcesRequest.Builder()
                    .setDataTypes(DataType.TYPE_STEP_COUNT_DELTA)
                    .setDataSourceTypes(DataSource.TYPE_RAW)
                    .build()
            )
            .addOnSuccessListener { dataSources ->
                dataSources.forEach {
                    Log.i(TAG, "Data source found: ${it.streamIdentifier}")
                    Log.i(TAG, "Data Source type: ${it.dataType.name}")

                    if (it.dataType == DataType.TYPE_STEP_COUNT_DELTA) {
                        Log.i(TAG, "Data source for STEP_COUNT_DELTA found!")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Find data sources request failed", e)
            }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buttonStart(): Boolean {
        change = true
        start.text = "Stop"

        Fitness.getSensorsClient(this, GoogleSignIn.getAccountForExtension(this, fitnessOptions))
            .add(
                SensorRequest.Builder()
                    // data sets.
                    .setDataType(DataType.TYPE_STEP_COUNT_DELTA) // Can't be omitted.
                    .setSamplingRate(selectedFps.toLong(), TimeUnit.MILLISECONDS)
                    .build(),
                listener
            )
            .addOnSuccessListener {
                Log.i(TAG, "Listener registered!")
            }
            .addOnFailureListener {
                Log.e(TAG, "Listener not registered.")
            }

        openFile()
        dataListner()
        return change
    }

    private fun buttonStop(): Boolean {
        change = false
        start.text = "Start"

        Fitness.getSensorsClient(this, GoogleSignIn.getAccountForExtension(this, fitnessOptions))
            .remove(listener)
            .addOnSuccessListener {
                Log.i(TAG, "Listener was removed!")
            }
            .addOnFailureListener {
                Log.i(TAG, "Listener was not removed.")
            }

        closeFile()
        stopDataListner()
        return change
    }

    fun openFile() {
        try {
            when (file.createNewFile()) {
                true -> Log.i("", "Success")
                false -> Log.i("", "Error")
            }

        } catch (e: Exception) {
            Log.e(TAG, "➡️ my error is -  ${e}")
        }
    }

    fun closeFile() {
        try {
            writer.flush()
            writer.close()
        } catch (e: Exception) {
            Log.e(TAG, "➡️ my error is -  ${e}")
        }
    }

    fun dataListner() {
        val sensor_gyroscope = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val sensor_accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensor_magnetic = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val check_gyroscope = manager?.registerListener(
            poseListener,
            sensor_gyroscope,
            SensorManager.SENSOR_DELAY_UI
        )
        val check_accelerometer = manager?.registerListener(
            poseListener,
            sensor_accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
        val check_magnetic = manager?.registerListener(
            poseListener,
            sensor_magnetic,
            SensorManager.SENSOR_DELAY_UI
        )

        if (check_gyroscope == false) {
            gyroscope.text = "Gyroscope don't working"
        }
        if (check_accelerometer == false) {
            accelerometer.text = "Accelerometer don't working"
        }
        if (check_magnetic == false) {
            compass.text = "Compass don't working"
        }
    }

    fun stopDataListner() {
        manager?.unregisterListener(poseListener)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_OK -> when (requestCode) {
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE -> accessGoogleFit()
                else -> {
                    // Result wasn't from Google Fit
                }
            }
            else -> {
                Log.i(TAG, "No Successful permissions from Google Fit")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun accessGoogleFit() {
        Log.i(TAG, "Successful permissions from Google Fit")
        subject.text = account.email
    }
}




