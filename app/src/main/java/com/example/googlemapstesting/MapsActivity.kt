package com.example.googlemapstesting

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.googlemapstesting.databinding.ActivityMapsBinding
import com.google.android.gms.maps.GoogleMap.OnMapClickListener
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener
import com.google.android.gms.maps.GoogleMap.OnMyLocationClickListener
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.GroundOverlayOptions
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.StrokeStyle
import com.google.android.gms.maps.model.StyleSpan
import com.google.maps.android.data.geojson.GeoJsonLayer
import org.json.JSONObject
import java.io.InputStream


class MapsActivity : AppCompatActivity(),
    OnMarkerClickListener, OnMapReadyCallback, OnMyLocationButtonClickListener,
    OnMyLocationClickListener, OnRequestPermissionsResultCallback,  OnMapClickListener,
    View.OnClickListener {
    //user location
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

    private val SWofScreen = LatLng(0.0,0.0)
    private val NEofScreen = LatLng(0.0, 0.0)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
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

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL


        // Check for location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            enableMyLocation()
        }


        loadGeoJsonFromResource(R.raw.us_states)
        loadGeoJsonFromResource(R.raw.countries)
        loadGeoJsonFromResource(R.raw.us_counties)

        // Add a marker in Sydney and move the camera


        //add bounds for australia
        val australia = LatLngBounds(
            LatLng((-44.0),113.0),//SW
            LatLng((-10.0), 154.0)//NE
        )

        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(australia.center, 5f))
        //mMap.setLatLngBoundsForCameraTarget(australia)

        // Construct a CameraPosition focusing on Mountain View and animate the camera to that position.
        /*val cameraPosition = CameraPosition.Builder()
            .target(mountainView) // Sets the center of the map to Mountain View
            .zoom(17f)            // Sets the zoom
            .bearing(90f)         // Sets the orientation of the camera to east
            .tilt(30f)            // Sets the tilt of the camera to 30 degrees
            .build()              // Creates a CameraPosition from the builder
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
         */
        markerPerth = mMap.addMarker(
            MarkerOptions()
                .position(PERTH)
                .title("Perth")
        )
        markerPerth?.tag = 0
        markerSydney = mMap.addMarker(
            MarkerOptions()
                .position(SYDNEY)
                .title("Sydney")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                .draggable(true)
        )
        markerSydney?.tag = 0
        markerBrisbane = mMap.addMarker(
            MarkerOptions()
                .position(BRISBANE)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                .title("Brisbane")
        )
        markerBrisbane?.tag = 0

        val pO = PolylineOptions()
            .add(BRISBANE)
            .add(SYDNEY)
            .add(PERTH)
            .color(Color.RED)

        //val polyline = mMap.addPolyline(pO)

        val p1 = PolygonOptions()
            .add(
                LatLng((-44.0),113.0),//SW
                LatLng((-44.0),154.0),//NW
                LatLng((-10.0), 154.0),//NE
                LatLng((-10.0),113.0),//SE
            )
            .strokeColor(Color.BLUE)
            .fillColor(Color.YELLOW)
            .addHole(listOf(
                LatLng((-43.0),114.0),//SW
                LatLng((-43.0),153.0),//NW
                LatLng((-11.0), 153.0),//NE
                LatLng((-11.0),114.0),//SE
            ))

        //val polygon = mMap.addPolygon(p1)

        val c1 = CircleOptions()
            .center(LatLng(-27.0,134.0))
            .radius(100000.0)
            .strokeColor(Color.CYAN)
            .strokeWidth(19f)

        //val circle = mMap.addCircle(c1)

        val pKoala = GroundOverlayOptions()
            .image(BitmapDescriptorFactory.fromResource(R.drawable.koala))
            .position(PERTH, 900f, 900f)

        mMap.addGroundOverlay(pKoala)

        // Set a listener for marker click.
        //mMap.setOnMarkerClickListener(this)
        mMap.setPadding(0, 0, 0, 150)
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(australia, 0))
        mMap.setOnMyLocationButtonClickListener(this)
        mMap.setOnMyLocationClickListener(this)
        mMap.setOnMapClickListener(this)
        Log.d("MAP", "MAP CREATED")
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val clickCount = marker.tag as? Int
        marker.showInfoWindow()
        clickCount?.let{
            val newCount = it+1
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
        Toast.makeText(this, "Current location:\n$location", Toast.LENGTH_LONG)
            .show()
    }

    override fun onMyLocationButtonClick(): Boolean {
        Toast.makeText(this, "MyLocation button clicked", Toast.LENGTH_SHORT)
            .show()
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }
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


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    enableMyLocation()
                } else {
                    // Permission was denied, handle accordingly
                    Log.e("User LOCATION", "PERMISSION DENIED")
                }
            }
        }
    }

    override fun onMapClick(p0: LatLng) {
        Log.d("MAP", "CLICKED")
        Toast.makeText(this, "lat: "+p0.latitude+ "\nlng: "+p0.longitude, Toast.LENGTH_LONG)
            .show()
    }

    override fun onClick(v: View?) {
        if(v is RadioButton){
            if(v.id == R.id.counties){
                Toast.makeText(this,"displaying only counties", Toast.LENGTH_SHORT).show()
                layers[R.raw.countries]?.removeLayerFromMap()
                layers[R.raw.us_states]?.removeLayerFromMap()
                layers[R.raw.us_counties]?.addLayerToMap()
            }
            else if(v.id==R.id.states){
                Toast.makeText(this,"displaying only states", Toast.LENGTH_SHORT).show()
                layers[R.raw.countries]?.removeLayerFromMap()
                layers[R.raw.us_counties]?.removeLayerFromMap()
                layers[R.raw.us_states]?.addLayerToMap()
            }else if (v.id == R.id.countries){
                Toast.makeText(this,"displaying only countries", Toast.LENGTH_SHORT).show()
                layers[R.raw.us_states]?.removeLayerFromMap()
                layers[R.raw.us_counties]?.removeLayerFromMap()
                layers[R.raw.countries]?.addLayerToMap()
            }
        }
    }

}