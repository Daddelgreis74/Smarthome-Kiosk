package com.example.smarthomekiosk

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log

class KioskNsdHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private val serviceType = "_smarthome-kiosk._tcp."
    private var registeredServiceName: String? = null

    fun registerService(port: Int) {
        if (registrationListener != null) return // Already registered

        val deviceName = Build.MODEL
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Smarthome Kiosk ($deviceName)"
            serviceType = this@KioskNsdHelper.serviceType
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                registeredServiceName = nsdServiceInfo.serviceName
                Log.i("KioskNsdHelper", "Service registered successfully: ${nsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(nsdServiceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("KioskNsdHelper", "Service registration failed, errorCode: $errorCode")
            }

            override fun onServiceUnregistered(nsdServiceInfo: NsdServiceInfo) {
                Log.i("KioskNsdHelper", "Service unregistered successfully: ${nsdServiceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(nsdServiceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("KioskNsdHelper", "Service unregistration failed, errorCode: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e("KioskNsdHelper", "Failed to register mDNS service", e)
        }
    }

    fun unregisterService() {
        val listener = registrationListener ?: return
        try {
            nsdManager.unregisterService(listener)
        } catch (e: Exception) {
            Log.e("KioskNsdHelper", "Failed to unregister mDNS service", e)
        } finally {
            registrationListener = null
            registeredServiceName = null
        }
    }
}
