package com.example.stepcounter

import android.Manifest
import android.annotation.SuppressLint
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
import com.google.android.gms.fitness.request.DataSourcesRequest
import com.google.android.gms.fitness.request.OnDataPointListener
import com.google.android.gms.fitness.request.SensorRequest
import java.io.File
import java.io.FileWriter
import java.util.concurrent.TimeUnit

import java.text.SimpleDateFormat
import java.util.*


const val TAG = "StepCounter"
const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1
const val MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION = 2


class MainActivity : AppCompatActivity() {

    val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
        .build()

    private lateinit var spinner_fps: Spinner
    private lateinit var start: Button
    private lateinit var steps: TextView
    private lateinit var gyroscope: TextView
    private lateinit var accelerometer: TextView
    private lateinit var compass: TextView
    private lateinit var subject: TextView

    private var sensor: SensorManager? = null

    private var floatGravity = FloatArray(3)
    private var floatGeoMagnetic = FloatArray(3)
    private var floatOrientation = FloatArray(3)
    private var floatRotationMatrix = FloatArray(9)

    var change = false

    //spinnerValue
    var fps = arrayOf("30", "40", "50", "60", "70")
    var total = 0
    var frameNumber = 0
    var rotation = 0
    var selectedFps:Long = 60
    var gyroscopeX:Float = 0.0F
    var gyroscopeY:Float = 0.0F
    var gyroscopeZ:Float = 0.0F
    var accelerometerX:Float = 0.0F
    var accelerometerY:Float = 0.0F
    var accelerometerZ:Float = 0.0F
    var magnetometerX:Float = 0.0F
    var magnetometerY:Float = 0.0F
    var magnetometerZ:Float = 0.0F

    lateinit var dateFormat: SimpleDateFormat
    lateinit var timestamp: String
    lateinit var file: File
    lateinit var writer: FileWriter
    lateinit var listener: OnDataPointListener
    lateinit var poseListener: SensorEventListener
    lateinit var account: GoogleSignInAccount

    @SuppressLint("SimpleDateFormat")
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
        dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss:Zmmm")
        timestamp = dateFormat.format(Date()) // Find todays date

        sensor = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        poseListener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

            }

            override fun onSensorChanged(event: SensorEvent?) {
                //get current gyroscope data
                if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
                    gyroscopeX = event.values[0]
                    gyroscopeY = event.values[1]
                    gyroscopeZ = event.values[2]
                    gyroscope.text = "Gyroscope x:$gyroscopeX y:$gyroscopeY z:$gyroscopeZ"
                }
                //get current accelerometer data
                if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    accelerometerX = event.values[0]
                    accelerometerY = event.values[1]
                    accelerometerZ = event.values[2]
                    accelerometer.text = "Accelerometer x:$accelerometerX y:$accelerometerY z:$accelerometerZ"
                    floatGravity = event.values
                    //get current rotation
                    val currentRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        this@MainActivity.display?.rotation
                    } else {
                        TODO("VERSION.SDK_INT < R")
                    }
                    rotation = when (currentRotation) {
                        0 -> 0
                        1 -> 90
                        2 -> 180
                        3 -> 270
                        else -> -1
                    }
                }
                //get current magnetometer data
                if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    floatGeoMagnetic = event.values
                    magnetometerX = floatGeoMagnetic[0]
                    magnetometerY = floatGeoMagnetic[1]
                    magnetometerZ = floatGeoMagnetic[2]

                    SensorManager.getRotationMatrix(
                        floatRotationMatrix,
                        null,
                        floatGravity,
                        floatGeoMagnetic
                    )
                    //get compass data
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
        // Step counter listener
        listener = OnDataPointListener { dataPoint ->
            for (field in dataPoint.dataType.fields) {
                val value = dataPoint.getValue(field)
                Log.i(TAG, "Detected DataPoint field: ${field.name}")
                Log.i(TAG, "Detected DataPoint value: $value")
                total += value.asInt()
                steps.text = "Steps: $total "
                //Timestamp
                dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss:'Z'mmm")
                timestamp = dateFormat.format(Date()) // Find today date
                //Write data to file
                writer.write(
                    "${frameNumber},${timestamp},${gyroscopeX},${gyroscopeY},${gyroscopeZ}" +
                            ",${accelerometerX},${accelerometerY},${accelerometerZ}"+
                            "${magnetometerX},${magnetometerY},${magnetometerZ},${rotation},${total}\n"
                )
                frameNumber++
            }
        }
        //AppRequestPermissions
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.GET_ACCOUNTS
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


        // Button event
        start.setOnClickListener(View.OnClickListener {
            change = when (change) {
                false -> {
                    file = File(
                        this.filesDir,
                        "${subject.text}_${timestamp}_${spinner_fps.selectedItem}_raw_sensor_data_and_steps.csv"
                    )
                    writer = FileWriter(file)
                    writer.write(
                        "FrameNumber,Timestamp,GyroscopeX,GyroscopeY,GyroscopeZ," +
                                "AccelerometerX,AccelerometerY,AccelerometerZ" +
                                ",MagnetometerX,MagnetometerY,MagnetometerZ,Rotation,StepCounter\n"
                    )
                    buttonStart()
                }
                true -> buttonStop()
            }
        })
        Fitness
            .getSensorsClient(
                this, GoogleSignIn.getAccountForExtension(this, fitnessOptions)
            )
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

        selectedFps = when (spinner_fps.selectedItem) {
            "30" -> 33
            "40" -> 25
            "50" -> 20
            "60" -> 16
            "70" -> 14
            else -> 0
        }

        change = true
        total = 0
        frameNumber = 0
        start.text = "Stop"
        //start collect data for StepCount
        Fitness
            .getSensorsClient(
                this, GoogleSignIn.getAccountForExtension(this, fitnessOptions)
            )
            .add(
                SensorRequest.Builder()
                    // data sets.
                    .setDataType(DataType.TYPE_STEP_COUNT_DELTA) // Can't be omitted.
                    .setSamplingRate(selectedFps, TimeUnit.MILLISECONDS)
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
        //Stop collect data for StepCount
        Fitness
            .getSensorsClient(
                this, GoogleSignIn.getAccountForExtension(this, fitnessOptions)
            )
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

    // Manage Sensor data for gyroscope/accelerometer/magnetometer
    fun dataListner() {
        val sensor_gyroscope = sensor?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val sensor_accelerometer = sensor?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensor_magnetic = sensor?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val check_gyroscope = sensor?.registerListener(
            poseListener,
            sensor_gyroscope,
            SensorManager.SENSOR_DELAY_UI
        )
        val check_accelerometer = sensor?.registerListener(
            poseListener,
            sensor_accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
        val check_magnetic = sensor?.registerListener(
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
        sensor?.unregisterListener(poseListener)
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
        subject.text = account.account?.name
    }
}




