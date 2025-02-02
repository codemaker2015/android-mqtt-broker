package `in`.codemaker2015.mqttbroker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import `in`.codemaker2015.mqttbroker.databinding.SettingsActivityBinding
import `in`.codemaker2015.mqttbroker.service.MqttService
import `in`.codemaker2015.mqttbroker.utils.AppPreferences
import `in`.codemaker2015.mqttbroker.utils.NetworkCallBack
import `in`.codemaker2015.mqttbroker.utils.Utils
import `in`.codemaker2015.mqttbroker.utils.Utils.generatePassword
import `in`.codemaker2015.mqttbroker.utils.Utils.getIPAddress
import `in`.codemaker2015.mqttbroker.utils.Utils.isMyServiceRunning
import `in`.codemaker2015.mqttbroker.utils.Utils.networkRequest


class MainActivity : AppCompatActivity(), OnSharedPreferenceChangeListener {

    private var TAG = MainActivity::class.java.simpleName

    private var _binding: SettingsActivityBinding? = null

    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppPreferences.firstRun) {
            initSharedPrefs()
            Log.d(TAG, "onCreate: its first time run")
        }
        setIP()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        _binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIntentLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    Log.d(TAG, "notification permission granted")
                } else {
                    Log.w(TAG, "notification permission not granted")
                    showSnackBar("Please grant Notification permission from App Settings")
                }
            }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()

        } else {
            title = savedInstanceState.getCharSequence(TAG)
        }
    }

    override fun onResume() {
        super.onResume()
        checkForInAppUpdate()
    }

    private fun checkForInAppUpdate() {
        val updateManager = AppUpdateManagerFactory.create(this)
        val appUpdateInfoTask = updateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener {
            if (it.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && it.isUpdateTypeAllowed(
                    AppUpdateType.IMMEDIATE
                )
            ) {
                updateManager.startUpdateFlowForResult(
                    it,
                    this,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                    0
                )
            }
        }
    }

    private var requestNotificationPermissionIntentLauncher: ActivityResultLauncher<Intent>? = null

    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var mPreferences: SharedPreferences
        private var host: EditTextPreference? = null
        private var port: EditTextPreference? = null
        private var username: EditTextPreference? = null
        private var password: EditTextPreference? = null
        private var authEnable: SwitchPreference? = null
        var brokerTurnOrOff: SwitchPreference? = null
        private val TAG = "SettingsFragment"

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            mPreferences = this.requireActivity().getSharedPreferences("MQTT Broker", MODE_PRIVATE)
            host = findPreference(getString(R.string.mqtt_host))
            port = findPreference(getString(R.string.mqtt_port))
            username = findPreference(getString(R.string.mqtt_username))
            password = findPreference(getString(R.string.mqtt_password))
            authEnable = findPreference(getString(R.string.mqtt_auth_status))
            brokerTurnOrOff = findPreference(getString(R.string.mqtt_broker_status))
            val connectivityManager =
                requireContext().getSystemService(ConnectivityManager::class.java) as ConnectivityManager
            connectivityManager.requestNetwork(networkRequest, NetworkCallBack())
            ContextCompat.registerReceiver(
                requireContext(),
                mqttReceiver,
                IntentFilter(Utils.MQTT_STATUS_ON_OR_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            ContextCompat.registerReceiver(
                requireContext(),
                networkReceiver,
                IntentFilter(Utils.NETWORK_BROADCAST_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        private val networkReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.extras != null) {
                    when (intent.extras?.getBoolean("status")) {
                        true -> {
                            Log.d(TAG, "onReceive: ")
                        }

                        false -> {
                            host?.text = "Not Set"
                            brokerTurnOrOff?.isChecked = false
                            (activity as MainActivity).stopService()
                        }

                        else -> {
                            host?.text = "Not Set"
                            brokerTurnOrOff?.isChecked = false
                            (activity as MainActivity).stopService()
                            Log.d(TAG, "onReceive: unknown error ")
                        }
                    }
                }

            }
        }
        private val mqttReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.i(TAG, "onReceive: ${intent?.extras?.getBoolean("status")}")
                if (intent?.extras?.getBoolean("status") == true) {
                    host?.text = intent.extras?.getString("host")
                    brokerTurnOrOff?.isChecked = true
                } else {
                    brokerTurnOrOff?.isChecked = false
                    host?.text = "Not Set"
                    AppPreferences.mqttBrokerStatus = false
                }
            }
        }

        override fun onResume() {
            if (host != null) {
                host?.text = AppPreferences.mqttHost
                port?.text = AppPreferences.mqttPort
                password?.text = AppPreferences.mqttPassword
                username?.text = AppPreferences.mqttUserName
                authEnable?.isChecked = AppPreferences.mqttAuthStatus
                brokerTurnOrOff?.isChecked = AppPreferences.mqttBrokerStatus
                if (AppPreferences.mqttBrokerStatus && !isMyServiceRunning(
                        requireContext(),
                        MqttService::class.java
                    )
                ) {
                    (activity as MainActivity).startService()
                }
            }
            super.onResume()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, p1: String?) {
        Log.d(TAG, "Preference changed. $p1")
        try {
            when (p1) {
                getString(R.string.mqtt_broker_status) -> {
                    Log.d(TAG, "Server status changed")
                    val status = sharedPreferences.getBoolean(p1, false)
                    Log.d(TAG, "Start Server?$status")
                    val broadcastIntent = Intent(Utils.MQTT_STATUS_ON_OR_OFF)
                    broadcastIntent.setPackage(packageName)
                    if (status) {
                        val type = Utils.isConnectedToWifi(this)
                        Log.d(TAG, "onSharedPreferenceChanged: $type")
                        if (Utils.isConnectedToWifi(this@MainActivity)) {
                            broadcastIntent.putExtra("host", getIPAddress(true))
                            broadcastIntent.putExtra("status", true)
                            AppPreferences.mqttBrokerStatus = true
                            Log.d(TAG, "Starting Server")
                            startService()
                        } else {
                            broadcastIntent.putExtra("status", false)
                            val contextView = findViewById<View>(android.R.id.content)
                            Snackbar.make(
                                contextView,
                                "MQTT will only work on Local Wifi Network",
                                Snackbar.LENGTH_LONG
                            ).show()
                            Log.d(TAG, "onSharedPreferenceChanged: Unsupported Network type")
                        }
                    } else {
                        broadcastIntent.putExtra("status", false)
                        AppPreferences.mqttBrokerStatus = false
                        Log.d(TAG, "Stopping Server")
                        stopService()
                    }
                    sendBroadcast(broadcastIntent)
                }

                getString(R.string.mqtt_auth_status) -> {
                    Log.d(TAG, "Restarting mqtt service")
                    Log.d(
                        TAG,
                        "onSharedPreferenceChanged: " + sharedPreferences.getBoolean(p1, false)
                    )
                    val status = sharedPreferences.getBoolean(p1, false)
                    AppPreferences.mqttAuthStatus = status
                    if (AppPreferences.mqttBrokerStatus) {
                        Thread {
                            stopService()
                            startService()
                        }.start()
                    }
                    showSnackBar("MQTT broker config updated.")

                }

                getString(R.string.mqtt_password), getString(R.string.mqtt_username), getString(R.string.mqtt_port) -> {
                    if (p1 == getString(R.string.mqtt_password)) {
                        AppPreferences.mqttPassword = sharedPreferences.getString(p1, "")
                    }
                    if (p1 == getString(R.string.mqtt_username)) {
                        AppPreferences.mqttUserName = sharedPreferences.getString(p1, "")
                    }
                    if (p1 == getString(R.string.mqtt_port)) {
                        AppPreferences.mqttPort = sharedPreferences.getString(p1, "")
                    }
                    showSnackBar("You need to restart the server for applying the config changes")
                }

                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "onSharedPreferenceChanged: $e")
        }
    }

    private fun initSharedPrefs() {
        AppPreferences.mqttHost = getIPAddress(true)
        AppPreferences.mqttPort = "1883"
        AppPreferences.mqttAuthStatus = false
        AppPreferences.mqttUserName = "codemaker2015"
        AppPreferences.mqttPassword = generatePassword()
        AppPreferences.firstRun = false
    }

    private fun startService() {
        Log.d(TAG, "Starting MQTT Service")

        // Sets up permissions request launcher.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    requestNotificationPermissionIntentLauncher?.launch(intent)
                } catch (ex: Exception) {
                    Log.e(TAG, "onCreate: ", ex)
                    showSnackBar(
                        ex.localizedMessage ?: "Unknown error occurred when requesting permission"
                    )
                }
                return
            }
        }

        val serviceIntent = Intent(this, MqttService::class.java)
        if (isMyServiceRunning(this, MqttService::class.java)) {
            stopService(serviceIntent)
        }
        if (AppPreferences.mqttBrokerStatus) {
            startForegroundService(serviceIntent)
        }
    }

    private fun showSnackBar(message: String) {
        val contextView = findViewById<View>(android.R.id.content)
        Snackbar.make(
            contextView,
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun stopService() {
        try {
            Log.d(TAG, "Stopping MQTT Service")
            val serviceIntent = Intent(this, MqttService::class.java)
            stopService(serviceIntent)
        } catch (ex: Exception) {
            Log.e(TAG, "stopService: ", ex)
        }
    }

    private fun setIP() {
        AppPreferences.mqttHost = getIPAddress(true)
    }

}
