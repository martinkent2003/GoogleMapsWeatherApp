package com.example.googlemapstesting

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
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
import com.google.android.gms.maps.GoogleMap.OnMapClickListener
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener
import com.google.android.gms.maps.GoogleMap.OnMyLocationClickListener
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.data.geojson.GeoJsonLayer

class MapsActivity : AppCompatActivity(),
    OnMarkerClickListener, OnMapReadyCallback, OnMyLocationButtonClickListener,
    OnMyLocationClickListener, OnRequestPermissionsResultCallback, OnMapClickListener,
    View.OnClickListener {

    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private var layers : HashMap<Int, GeoJsonLayer> = HashMap<Int, GeoJsonLayer>()
    private val PERTH = LatLng(-31.952854, 115.857342)
    private val SYDNEY = LatLng(-33.87365, 151.20689)
    private val BRISBANE = LatLng(-27.47093, 153.0235)
    private var markerPerth: Marker? = null
    private var markerSydney: Marker? = null
    private var markerBrisbane: Marker? = null
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var clusterManager: ClusterManager<MyItem>
    private val tornadoItems = mutableListOf<MyItem>()  // Keep track of all tornado items
    private var isClusterVisible = true

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

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val counties = findViewById<RadioButton>(R.id.counties)
        val states = findViewById<RadioButton>(R.id.states)
        val countries = findViewById<RadioButton>(R.id.countries)

        counties.setOnClickListener(this)
        states.setOnClickListener(this)
        countries.setOnClickListener(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            enableMyLocation()
        }

        val australia = LatLngBounds(
            LatLng(-44.0,113.0),
            LatLng(-10.0, 154.0)
        )

        markerPerth = mMap.addMarker(
            MarkerOptions().position(PERTH).title("Perth")
        )
        markerSydney = mMap.addMarker(
            MarkerOptions().position(SYDNEY).title("Sydney").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)).draggable(true)
        )
        markerBrisbane = mMap.addMarker(
            MarkerOptions().position(BRISBANE).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)).title("Brisbane")
        )

        val pO = PolylineOptions().add(BRISBANE).add(SYDNEY).add(PERTH).color(Color.RED)
        val p1 = PolygonOptions()
            .add(
                LatLng(-44.0,113.0),
                LatLng(-44.0,154.0),
                LatLng(-10.0, 154.0),
                LatLng(-10.0,113.0)
            )
            .strokeColor(Color.BLUE)
            .fillColor(Color.YELLOW)
            .addHole(listOf(
                LatLng(-43.0,114.0),
                LatLng(-43.0,153.0),
                LatLng(-11.0, 153.0),
                LatLng(-11.0,114.0)
            ))

        val c1 = CircleOptions().center(LatLng(-27.0,134.0)).radius(100000.0).strokeColor(Color.CYAN).strokeWidth(19f)
        val pKoala = GroundOverlayOptions().image(BitmapDescriptorFactory.fromResource(R.drawable.koala)).position(PERTH, 900f, 900f)

        mMap.addGroundOverlay(pKoala)
        mMap.setPadding(0, 0, 0, 150)
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(australia, 0))
        mMap.setOnMyLocationButtonClickListener(this)
        mMap.setOnMyLocationClickListener(this)
        mMap.setOnMapClickListener(this)

        setUpClusterer()
        Log.d("MAP", "MAP CREATED")
        val tornadoManager = TornadoManager(1)
        addTornadoes(tornadoManager)
        loadGeoJsonFromResource(R.raw.us_states)
        loadGeoJsonFromResource(R.raw.countries)
        loadGeoJsonFromResource(R.raw.us_counties)
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
        Toast.makeText(this, "lat: ${p0.latitude}\nlng: ${p0.longitude}", Toast.LENGTH_LONG).show()
    }

    override fun onClick(v: View?) {
        if (v is RadioButton) {
            when (v.id) {
                R.id.counties -> {
                    Toast.makeText(this, "displaying only counties", Toast.LENGTH_SHORT).show()
                    layers[R.raw.countries]?.removeLayerFromMap()
                    layers[R.raw.us_states]?.removeLayerFromMap()
                    layers[R.raw.us_counties]?.addLayerToMap()
                    toggleClusterVisibility(false)
                }
                R.id.states -> {
                    Toast.makeText(this, "displaying only states", Toast.LENGTH_SHORT).show()
                    layers[R.raw.countries]?.removeLayerFromMap()
                    layers[R.raw.us_counties]?.removeLayerFromMap()
                    layers[R.raw.us_states]?.addLayerToMap()
                    toggleClusterVisibility(false)
                }
                R.id.countries -> {
                    Toast.makeText(this, "displaying only countries", Toast.LENGTH_SHORT).show()
                    layers[R.raw.us_states]?.removeLayerFromMap()
                    layers[R.raw.us_counties]?.removeLayerFromMap()
                    layers[R.raw.countries]?.addLayerToMap()

                    toggleClusterVisibility(false)

                }
                R.id.tornadoes -> {
                    Toast.makeText(this, "displaying only tornadoes", Toast.LENGTH_SHORT).show()
                    layers[R.raw.us_states]?.removeLayerFromMap()
                    layers[R.raw.us_counties]?.removeLayerFromMap()
                    layers[R.raw.countries]?.removeLayerFromMap()
                    toggleClusterVisibility(true)
                }
            }
        }
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
                    clusterManager.addItem(tornadoItem)
                }
            }
            clusterManager.cluster()
        }
    }

    private fun toggleClusterVisibility(visible: Boolean) {
        if(isClusterVisible == visible) return
        if (isClusterVisible && !visible) {
            clusterManager.clearItems()
            clusterManager.cluster()
            isClusterVisible = false
        } else if(!isClusterVisible && visible) {
            for (item in tornadoItems) {
                clusterManager.addItem(item)
            }
            clusterManager.cluster()
            isClusterVisible = true
        }else{
            Log.d("TOGGLECLUSTER", "YOUR LOGIC IS WRONG")
        }
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
}
