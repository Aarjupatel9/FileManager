package com.mhk.filemanager

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
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mhk.filemanager.Entities.Constants.SORT_CONSTANTS
import com.mhk.filemanager.Entities.Constants.notificationId
import com.mhk.filemanager.Entities.Constants.notificationName
import com.mhk.filemanager.databinding.ActivityMainBinding
import com.mhk.filemanager.services.MyBroadcastReceiver
import com.mhk.filemanager.services.MyJobService
import com.mhk.filemanager.viewmodal.FileManagerViewModel
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
            showSortDialog()
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

    private fun showSortDialog() {
        val dialogView = layoutInflater.inflate(R.layout.sort_dialog, null)
        val dialog = Dialog(this)
        dialog.setContentView(dialogView)

        // Apply the rounded background and set the dialog width
        dialog.window?.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.dialog_rounded_bg))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)


        val nameAsc = dialogView.findViewById<ImageButton>(R.id.sortByNameAsc)
        val nameDesc = dialogView.findViewById<ImageButton>(R.id.sortByNameDesc)
        val sizeAsc = dialogView.findViewById<ImageButton>(R.id.sortBySizeAsc)
        val sizeDesc = dialogView.findViewById<ImageButton>(R.id.sortBySizeDesc)
        val dateAsc = dialogView.findViewById<ImageButton>(R.id.sortByDateAsc)
        val dateDesc = dialogView.findViewById<ImageButton>(R.id.sortByDateDesc)

        val buttons = mapOf(
            SORT_CONSTANTS.SORT_BY_NAME_ASC to nameAsc,
            SORT_CONSTANTS.SORT_BY_NAME_DESC to nameDesc,
            SORT_CONSTANTS.SORT_BY_SIZE_ASC to sizeAsc,
            SORT_CONSTANTS.SORT_BY_SIZE_DESC to sizeDesc,
            SORT_CONSTANTS.SORT_BY_DATE_ASC to dateAsc,
            SORT_CONSTANTS.SORT_BY_DATE_DESC to dateDesc
        )

        // Highlight the currently active sort button
        val activeColor = getColorFromTheme(this, com.google.android.material.R.attr.colorPrimary)
        buttons[sortOrder]?.imageTintList = ColorStateList.valueOf(activeColor)

        buttons.forEach { (sortType, button) ->
            button.setOnClickListener {
                sortOrder = sortType
                viewModel.openedFile.value?.let { path ->
                    fileAdapter.loadMediaFiles(path, sortOrder)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }


    private fun initialFileManagementTasks() {

        viewModel.openedFileTree.observe(this) { updatedOpenedFileTree ->
            Log.d(TAG,"ViewModel observe Opened File Tree : $updatedOpenedFileTree")
            populateLinearLayout(
                fileTreeLayout, updatedOpenedFileTree
            )
        }
    }

    private fun populateLinearLayout(linearLayout: LinearLayout, openedFileTree: List<List<String>>) {
        linearLayout.removeAllViews()

        for (i in openedFileTree.indices) {
            val textView = TextView(linearLayout.context)
            textView.text = openedFileTree[i][1]
            textView.setPadding(16, 8, 16, 8)
            textView.textSize = 16f
            val colorPrimary = getColorFromTheme(linearLayout.context, com.google.android.material.R.attr.colorPrimary)
            textView.setTextColor(colorPrimary)

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(2, 0, 2, 0)
            }
            textView.layoutParams = layoutParams

            textView.setOnClickListener {
                fileAdapter.loadMediaFiles(openedFileTree[i][0])
            }

            linearLayout.addView(textView)

            if (i < openedFileTree.size - 1) {
                val imageView = ImageView(linearLayout.context)
                imageView.setImageResource(R.drawable.baseline_arrow_forward_ios_24)

                val typedValue = TypedValue()
                linearLayout.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                val arrowColor = typedValue.data
                imageView.setColorFilter(arrowColor)

                val imageParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 8, 0)
                }
                imageView.layoutParams = imageParams
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
            if (!Environment.isExternalStorageManager()) {
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
                    m.invoke(o, "eth0")
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
            .setPeriodic(20 * 60 * 1000)
            .setPersisted(true)
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
        var sortOrder: Int = SORT_CONSTANTS.SORT_BY_NAME_ASC
    }

    private fun checkNotificationPermission() {
    }
}
