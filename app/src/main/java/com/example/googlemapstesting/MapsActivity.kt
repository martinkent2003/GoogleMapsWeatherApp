package com.example.googlemapstesting

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.example.googlemapstesting.databinding.ActivityMapsBinding
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener
import com.google.android.gms.maps.GoogleMap.OnMapClickListener
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener
import com.google.android.gms.maps.GoogleMap.OnMyLocationClickListener
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.data.geojson.GeoJsonLayer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MapsActivity : AppCompatActivity(),
    OnMarkerClickListener, OnMapReadyCallback, OnMyLocationButtonClickListener,
    OnMyLocationClickListener, OnRequestPermissionsResultCallback, OnMapClickListener, OnCameraIdleListener{

    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private var layers : HashMap<Int, GeoJsonLayer> = HashMap<Int, GeoJsonLayer>()
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var clusterManager: ClusterManager<MyItem>
    private val tornadoItems = mutableListOf<MyItem>()  // Keep track of all tornado items
    private lateinit var counties : CheckBox
    private lateinit var states : CheckBox
    private lateinit var countries : CheckBox
    private lateinit var tornadoes : CheckBox
    private var tornadoLoadJob: Job? = null
    var tornadoManager : TornadoManager? = null
    private lateinit var sharedPref : SharedPreferences

    companion object {
        private const val PREF_COUNTIES_CHECKED = "pref_counties_checked"
        private const val PREF_STATES_CHECKED = "pref_states_checked"
        private const val PREF_COUNTRIES_CHECKED = "pref_countries_checked"
        private const val PREF_TORNADOES_CHECKED = "pref_tornadoes_checked"
    }

    private val coroutineExceptionHandler = CoroutineExceptionHandler{ _, exception ->
        Log.e("CoroutineException", "Error occurred: ${exception.message}")
    }

    inner class MyItem(
        lat: Double,
        lng: Double,
        title: String,
        snippet: String
    ) : ClusterItem {
        private val position: LatLng = LatLng(lat, lng)
        private val title: String = title
        private val snippet: String = snippet
        override fun getPosition(): LatLng {
            return position
        }
        override fun getTitle(): String? {
            return title
        }
        override fun getSnippet(): String? {
            return snippet
        }
        override fun getZIndex(): Float? {
            return 0f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPref = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }

    private fun initializeSharedPreferences() {
        val countiesChecked = sharedPref.getBoolean(PREF_COUNTIES_CHECKED, false)
        val statesChecked = sharedPref.getBoolean(PREF_STATES_CHECKED, false)
        val countriesChecked = sharedPref.getBoolean(PREF_COUNTRIES_CHECKED, false)
        val tornadoesChecked = sharedPref.getBoolean(PREF_TORNADOES_CHECKED, false)

        counties.isChecked = countiesChecked
        states.isChecked = statesChecked
        countries.isChecked = countriesChecked
        tornadoes.isChecked = tornadoesChecked

        initializeLayersAndMarkers()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        //setup marker clusterer
        setUpClusterer()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            enableMyLocation()
        }

        mMap.setOnMyLocationButtonClickListener(this)
        mMap.setOnMyLocationClickListener(this)
        mMap.setOnMapClickListener(this)
        mMap.setOnCameraIdleListener(this)

        //get view checkboxes
        counties = findViewById<CheckBox>(R.id.counties)
        states = findViewById<CheckBox>(R.id.states)
        countries = findViewById<CheckBox>(R.id.countries)
        tornadoes = findViewById<CheckBox>(R.id.tornadoes)

        startTornadoJob()
        //load geoJsonLayers
        loadGeoJsonFromResource(R.raw.us_states)
        loadGeoJsonFromResource(R.raw.countries)
        loadGeoJsonFromResource(R.raw.us_counties)
        //add menu listeners and checkbox listeners, and then initialize the previous state values
        removeMenuListeners()
        initializeSharedPreferences()
        mMap.moveCamera(CameraUpdateFactory.newLatLng(LatLng(26.0, -80.1)))

    }

    private fun loadGeoJsonFromResource(resourceId: Int) {
        try {
            val layer = GeoJsonLayer(mMap, resourceId, baseContext)
            layers.put(resourceId, layer)
            layer.setOnFeatureClickListener { feature ->
                val fid = feature.id
                val name = feature.properties.first().toString().substring(5)
                if(fid != null){
                    Toast.makeText(this, fid +": "+ name, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    enableMyLocation()
                } else {
                    Log.e("User LOCATION", "PERMISSION DENIED")
                }
            }
        }
    }

    override fun onMapClick(p0: LatLng) {
        Log.d("MAP", "CLICKED")
        Log.d("TESTING", "CLICKED")
        Toast.makeText(this, "lat: ${p0.latitude}\nlng: ${p0.longitude}", Toast.LENGTH_LONG).show()
    }

    private fun setUpClusterer() {
        clusterManager = ClusterManager(this, mMap)
        mMap.setOnCameraIdleListener(clusterManager)
        mMap.setOnMarkerClickListener(clusterManager)
    }

    private fun addTornadoes(tornadoManager: TornadoManager) {
        tornadoManager.makeHTTPRequest { tornadoResponse ->
            for (feature in tornadoResponse.features) {
                val lat = feature.attributes.lat
                val long = feature.attributes.long
                val location = feature.attributes.location
                val county = feature.attributes.county
                val state = feature.attributes.state
                val comments = feature.attributes.comments

                if (lat != null && long != null) {
                    val title = "$location, $county, $state"
                    val snippet = comments ?: "No additional information available."
                    val tornadoItem = MyItem(lat, long, title, snippet)
                    tornadoItems.add(tornadoItem)
                }
            }
        }
    }

    fun startTornadoJob(){
        stopTornadoJob()
        tornadoManager = TornadoManager(System.currentTimeMillis().toInt())
        // reload tornado layers every half hour
        tornadoLoadJob = MainScope().launch{
            while(true){
                CoroutineScope(Dispatchers.IO + coroutineExceptionHandler).launch {
                    Log.d("Tornado Coroutine", "loading tornadoes")
                    addTornadoes(tornadoManager!!)
                }
                delay(1000 * 60 * 30)
            }
        }
    }
    fun stopTornadoJob(){
        tornadoLoadJob?.cancel()
        clusterManager.clearItems()
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val clickCount = marker.tag as? Int
        marker.showInfoWindow()
        clickCount?.let {
            val newCount = it + 1
            marker.tag = newCount
            Toast.makeText(
                this,
                "${marker.title} has been clicked $newCount times.",
                Toast.LENGTH_SHORT
            ).show()
        }
        return false
    }

    override fun onMyLocationClick(location: Location) {
        Toast.makeText(this, "Current location:\n$location", Toast.LENGTH_LONG).show()
    }

    override fun onMyLocationButtonClick(): Boolean {
        Toast.makeText(this, "MyLocation button clicked", Toast.LENGTH_SHORT).show()
        return false
    }

    private fun initializeLayersAndMarkers() {
        if (tornadoes.isChecked) {
            Log.d("Cluster", "SHOW TORNADOES")
            for (item in tornadoItems) {
                clusterManager.addItem(item)
            }
            clusterManager.cluster()
        }

        if (countries.isChecked) {
            layers[R.raw.countries]?.addLayerToMap()
        } else {
            layers[R.raw.countries]?.removeLayerFromMap()
        }

        if (states.isChecked) {
            layers[R.raw.us_states]?.addLayerToMap()
        } else {
            layers[R.raw.us_states]?.removeLayerFromMap()
        }

        if (counties.isChecked) {
            layers[R.raw.us_counties]?.addLayerToMap()
        } else {
            layers[R.raw.us_counties]?.removeLayerFromMap()
        }
    }

    //remove menu listeners
    private fun removeMenuListeners(){
        // next two listeners are for the naming container
        findViewById<View>(R.id.removal_button).setOnClickListener{
            findViewById<View>(R.id.greyed_background_remove).visibility= View.VISIBLE
        }
        findViewById<View>(R.id.greyed_background_remove).setOnClickListener{
            it.visibility = View.GONE
        }
        findViewById<View>(R.id.remove_container).setOnClickListener{
                //this is purely so that clicking on it doesn't close the window
        }

        tornadoes.setOnCheckedChangeListener{_, isChecked ->
            if (!isChecked) {
                clusterManager.clearItems()
                clusterManager.cluster()
                Log.d("Cluster", "DELETE TORNADOES")
            } else {
                Log.d("Cluster", "SHOW TORNADOES")
                for (item in tornadoItems) {
                    clusterManager.addItem(item)
                }
                clusterManager.cluster()
            }
        }

        countries.setOnCheckedChangeListener{_, isChecked ->
            if (!isChecked)
                layers[R.raw.countries]?.removeLayerFromMap()
            else
                layers[R.raw.countries]?.addLayerToMap()
        }
        states.setOnCheckedChangeListener{_, isChecked ->
            if (!isChecked)
                layers[R.raw.us_states]?.removeLayerFromMap()
            else
                layers[R.raw.us_states]?.addLayerToMap()
        }
        counties.setOnCheckedChangeListener{_, isChecked ->
            if (!isChecked)
                layers[R.raw.us_counties]?.removeLayerFromMap()
            else
                layers[R.raw.us_counties]?.addLayerToMap()
        }
    }

    override fun onDestroy(){
        super.onDestroy()
        val editor = sharedPref.edit()
        editor.putBoolean(PREF_COUNTIES_CHECKED, counties.isChecked)
        editor.putBoolean(PREF_STATES_CHECKED, states.isChecked)
        editor.putBoolean(PREF_COUNTRIES_CHECKED, countries.isChecked)
        editor.putBoolean(PREF_TORNADOES_CHECKED, tornadoes.isChecked)
        editor.apply()
    }

    override fun onCameraIdle() {
        var mapBounds = mMap.projection.visibleRegion.latLngBounds
        var sw = mapBounds?.southwest
        var ne = mapBounds?.northeast

        if (sw != null && ne != null){
            Log.d("BOUNDS", "SW : $sw, NE : $ne")
        }
    }
}