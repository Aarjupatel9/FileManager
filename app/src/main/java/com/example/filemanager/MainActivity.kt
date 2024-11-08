package com.example.filemanager

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanager.Entities.Constants.SORT_CONSTANTS
import com.example.filemanager.Entities.Constants.notificationId
import com.example.filemanager.Entities.Constants.notificationName
import com.example.filemanager.Entities.FileEntry
import com.example.filemanager.databinding.ActivityMainBinding
import com.example.filemanager.services.MyBroadcastReceiver
import com.example.filemanager.services.MyJobService
import com.example.filemanager.viewmodal.FileManagerViewModel
import java.lang.reflect.InvocationTargetException

class MainActivity : AppCompatActivity() {

    private val TAG = "mainActivity"

    private lateinit var viewModel: FileManagerViewModel

    private lateinit var recyclerView: RecyclerView
    private lateinit var fileTreeLayout: LinearLayout
    private lateinit var fileAdapter: FileAdapter
    private lateinit var binding: ActivityMainBinding

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_home)
        startMain()
    }

    @SuppressLint("InflateParams")
    private fun startMain() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileTreeLayout = binding.fileTreeLayout
        recyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)

        viewModel = ViewModelProvider(this)[FileManagerViewModel::class.java]

        fileAdapter = FileAdapter(this, viewModel)
        recyclerView.adapter = fileAdapter
        Log.d("MainActivity", "Before Requesting permission : ${fileAdapter.itemCount}")

        checkManageExternalStoragePermission()
        initialNotificationSetup()
        startApplicationServices()
        checkNotificationPermission()
        initialFileManagementTasks()

        binding.sortButton.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.sort_dialog, null)
            val sortRadioGroup = dialogView.findViewById<RadioGroup>(R.id.sortRadioGroup)

            when (sortOrder) {
                SORT_CONSTANTS.SORT_BY_NAME_ASC -> sortRadioGroup.check(R.id.sortByNameAsc)
                SORT_CONSTANTS.SORT_BY_NAME_DESC -> sortRadioGroup.check(R.id.sortByNameDesc)
                SORT_CONSTANTS.SORT_BY_SIZE_ASC -> sortRadioGroup.check(R.id.sortBySizeAsc)
                SORT_CONSTANTS.SORT_BY_SIZE_DESC -> sortRadioGroup.check(R.id.sortBySizeDesc)
                SORT_CONSTANTS.SORT_BY_DATE_ASC -> sortRadioGroup.check(R.id.sortByDateAsc)
                SORT_CONSTANTS.SORT_BY_DATE_DESC -> sortRadioGroup.check(R.id.sortByDateDesc)
                else -> sortRadioGroup.check(R.id.sortByNameAsc)
            }

            val dialog = Dialog(MainActivity@ this)
            dialog.setContentView(dialogView)
            dialog.show()

            sortRadioGroup.setOnCheckedChangeListener { _, checkedId ->
                sortOrder = when (checkedId) { // Use checkedId.id to get the ID
                    R.id.sortByNameAsc -> SORT_CONSTANTS.SORT_BY_NAME_ASC
                    R.id.sortByNameDesc -> SORT_CONSTANTS.SORT_BY_NAME_DESC
                    R.id.sortBySizeAsc -> SORT_CONSTANTS.SORT_BY_SIZE_ASC
                    R.id.sortBySizeDesc -> SORT_CONSTANTS.SORT_BY_SIZE_DESC
                    R.id.sortByDateAsc -> SORT_CONSTANTS.SORT_BY_DATE_ASC
                    R.id.sortByDateDesc -> SORT_CONSTANTS.SORT_BY_DATE_DESC
                    else -> SORT_CONSTANTS.SORT_BY_NAME_ASC
                }
                viewModel.openedFile.value?.let { it1 ->
                    fileAdapter.loadMediaFiles(it1, sortOrder)
                }

                dialog.dismiss()
            }

            dialog.show()
        }
        binding.mainActivityBackButton.setOnClickListener{
            val size = viewModel.openedFileTree.value?.size
            if (size != null) {
                if(size>1){
                    val parentFile = viewModel.openedFileTree.value?.get(size-2)
                    if (parentFile != null) {
                        fileAdapter.loadMediaFiles(parentFile[0]);
                    }
                }
            }
        }
    }

    private fun initialFileManagementTasks() {

        viewModel.openedFileTree.observe(this) { updatedOpenedFileTree ->
            // Update UI or call FileAdapter functions with the new openedFileTree
            Log.d(TAG,"ViewModel observe Opened File Tree : $updatedOpenedFileTree")
            populateLinearLayout(
                fileTreeLayout, updatedOpenedFileTree
            ) // Assuming you have populateLinearLayout
        }
    }

    private fun populateLinearLayout(linearLayout: LinearLayout, openedFileTree: List<List<String>>) {
        linearLayout.removeAllViews() // Clear existing views

        // Loop through the openedFileTree list and create TextViews and ImageViews
        for (i in openedFileTree.indices) {
            // Create a new TextView for each fileInfo
            val textView = TextView(linearLayout.context)

            // Set the text from the fileInfo
            textView.text = openedFileTree[i][1]

            // Set padding
            textView.setPadding(16, 8, 16, 8) // Padding for spacing

            // Set text size and color (similar to XML attributes)
            textView.textSize = 16f // Text size in sp
            val colorPrimary = getColorFromTheme(linearLayout.context, R.attr.colorPrimary)

            textView.setTextColor(colorPrimary) // Text color

            // Set layout parameters programmatically
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                // Set horizontal margin
                setMargins(2, 0, 2, 0) // Equivalent to `android:layout_marginHorizontal="2sp"`
                weight = 1f  // Equivalent to `android:layout_weight="1"`
            }
            textView.layoutParams = layoutParams

            // Set onClickListener for the TextView
            textView.setOnClickListener {
                // You can uncomment this line to call the function when TextView is clicked
                // viewModel.updateOpenedFileTreeData(openedFileTree[i])
                fileAdapter.loadMediaFiles(openedFileTree[i][0]) // Load the media file on click
            }

            // Add the TextView to the LinearLayout
            linearLayout.addView(textView)

            // Add a vector image between the TextViews
            if (i < openedFileTree.size - 1) { // Add image between TextViews, not after the last one
                val imageView = ImageView(linearLayout.context)

                // Set the vector image (use a vector drawable resource)
                imageView.setImageResource(R.drawable.baseline_arrow_forward_ios_24) // Replace with your vector drawable

                // Set layout params for the ImageView
                val imageParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    // Set margin between the image and TextView
                    setMargins(8, 0, 8, 0) // Equivalent to `android:layout_marginHorizontal="8dp"`
                }

                // Set the layout parameters to the imageView
                imageView.layoutParams = imageParams

                // Add the ImageView to the LinearLayout
                linearLayout.addView(imageView)
            }
        }
    }
    private fun getColorFromTheme(context: Context, attributeId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attributeId, typedValue, true)
        return typedValue.data
    }

    private fun checkManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasPermission = Environment.isExternalStorageManager()
            if (!hasPermission) {
                // Prompt user to grant manage external storage permission
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }

    private fun startTethering(ctx: Context) {
        val o = ctx.getSystemService(Context.CONNECTIVITY_SERVICE)
        Log.d("HotspotManager", "m : ${o.javaClass.methods.size}")
        for (m in o.javaClass.methods) {
            Log.d(
                "HotspotManager",
                "m : ${m.name} ${m.defaultValue} returnType: ${m.returnType} ${m.isDefault} ${m.parameters} typeParameter:  ${m.typeParameters} ${m.parameterTypes} modifiers: ${m.annotations} ${m.isAccessible} isSynthetic: ${m.isSynthetic} ${m.modifiers}"
            )
            if (m.name.equals("tether")) {
                try {
                    m.invoke(o, "eth0") // or whatever you know the iface to be
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                } catch (e: InvocationTargetException) {
                    val target = e.targetException
                    Log.e("tethering", "target: ${target.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun initialNotificationSetup() {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val chatChannel = NotificationChannel(notificationId, notificationName, importance)
        chatChannel.description = "To get alert notification for important chat messages"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(chatChannel)
    }

    private fun startApplicationServices() {
        val bluetoothStateIntentListener = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(MyBroadcastReceiver(), bluetoothStateIntentListener)

        if (!isNotificationServiceEnabled(this)) {
            Toast.makeText(
                this,
                "Please give notification listener permission to run application services",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            startActivity(intent)
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val requiredPermissions = arrayOf(
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.CHANGE_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH,
                )
                ActivityCompat.requestPermissions(
                    this, requiredPermissions, 102
                )
            } else {
                scheduleJob()
            }
        }
    }

    private fun scheduleJob() {
        val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val componentName = ComponentName(this, MyJobService::class.java)
        val jobInfo = JobInfo.Builder(1, componentName)
            .setPeriodic(20 * 60 * 1000) // 15 minutes (minimum allowed interval)
            .setPersisted(true) // Job will survive device reboots
            .build()
        val resultCode = jobScheduler.schedule(jobInfo)
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Job scheduled successfully!")
        } else {
            Log.e(TAG, "Job scheduling failed!")
        }
    }

    private fun isNotificationServiceEnabled(c: Context): Boolean {
        val pkgName = c.packageName
        val flat: String = Settings.Secure.getString(
            c.contentResolver, "enabled_notification_listeners"
        )
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (i in names.indices) {
                val cn = ComponentName.unflattenFromString(names[i])
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    companion object {
        var sortOrder: Int = 1
    }

    private fun checkNotificationPermission() {
    }
}