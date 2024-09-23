package com.example.filemanager.services

import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.lang.reflect.InvocationTargetException


class HotspotManager : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

        startTethering(this)
    }

    private fun startTethering(ctx: Context) {
        val o = ctx.getSystemService(Context.CONNECTIVITY_SERVICE)
        for (m in o.javaClass.methods) {
            Log.d("HotspotManager", "m : ${m.name} ${m.defaultValue} returnType: ${m.returnType} ${m.isDefault} ${m.parameters} typeParameter:  ${m.typeParameters} ${m.parameterTypes} modifiers: ${m.annotations} ${m.isAccessible} isSynthetic: ${m.isSynthetic} ${m.modifiers}")
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

}