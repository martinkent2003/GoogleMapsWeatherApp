package com.example.googlemapstesting

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class TornadoManager() {
    data class Attribute(
        @SerializedName("OBJECTID") val objectId: Int?,
        @SerializedName("UTC_DATETIME") val utcDateTime: Long?,
        @SerializedName("F_SCALE") val fScale: Int?,
        @SerializedName("LOCATION") val location: String?,
        @SerializedName("COUNTY") val county: String?,
        @SerializedName("STATE") val state: String?,
        @SerializedName("LATITUDE") val lat: Double?,
        @SerializedName("LONGITUDE") val long: Double?,
        @SerializedName("COMMENTS") val comments: String?
    )

    data class Geometry(
        val x: Double?,
        val y: Double?
    )

    data class Tornado(
        val attributes: Attribute,
        val geometry: Geometry
    )

    data class TornadoResponse(
        val features: List<Tornado>
    )

    fun makeHTTPRequest(callback: (TornadoResponse) -> Unit) {
        val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e("TornadoCoroutine", throwable.toString())
        }

        CoroutineScope(Dispatchers.IO + coroutineExceptionHandler).launch {
            Log.d("TornadoesCoroutine", "RUNNING")

            val client = OkHttpClient()
            val tUrl = "https://services9.arcgis.com/RHVPKKiFTONKtxq3/arcgis/rest/services/NOAA_storm_reports_v1/FeatureServer/3/query?where=1%3D1&outFields=*&f=json"

            val request = Request.Builder().url(tUrl).build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e("HTTP", "error making request")
                } else {
                    response.body?.let { responseBody ->
                        val responseBodyString = responseBody.string()  // Convert to string for logging
                        Log.d("TornadoRESPONSE", responseBodyString)

                        val gson = Gson()
                        val tornadoResponse = gson.fromJson(responseBodyString, TornadoResponse::class.java)
                        callback(tornadoResponse)
                    }
                }
            } catch (e: IOException) {
                Log.e("HTTP", "IOException in HTTP Tornado Call", e)
            }
        }
    }
}
