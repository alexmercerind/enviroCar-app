package com.alexmercerind.example

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.envirocar.map.MapView
import org.envirocar.map.camera.CameraUpdateFactory
import org.envirocar.map.location.LocationIndicator
import org.envirocar.map.location.LocationIndicatorCameraMode
import org.envirocar.map.model.Animation
import org.envirocar.map.model.Marker
import org.envirocar.map.model.Point
import org.envirocar.map.model.Polyline
import org.envirocar.map.provider.mapbox.MapboxMapProvider

class MainActivity : AppCompatActivity() {
    private val points = listOf(
        listOf(
            Point(52.516402, 13.379509),
            Point(52.516267, 13.379536),
            Point(52.516848, 13.388827),
            Point(52.527168, 13.387228),
            Point(52.531264, 13.382194),
            Point(52.532053, 13.384354),
            Point(52.533383, 13.387578),
            Point(52.535116, 13.389691)
        ),
        listOf(
            Point(52.516402, 13.379509),
            Point(52.516267, 13.379536),
            Point(52.517587, 13.398930),
            Point(52.518882, 13.402690),
            Point(52.519918, 13.404811),
            Point(52.517375, 13.408312),
            Point(52.518108, 13.409911),
            Point(52.518772, 13.411246),
            Point(52.515834, 13.412325),
            Point(52.515468, 13.417996),
            Point(52.510193, 13.430113),
            Point(52.508457, 13.434632),
            Point(52.504617, 13.441002)
        )
    )
    private val colors = listOf(
        0xFF9400D3.toInt(),
        0xFF4B0082.toInt(),
        0xFF0000FF.toInt(),
        0xFF00FF00.toInt(),
        0xFFFFFF00.toInt(),
        0xFFFF7F00.toInt(),
        0xFFFF0000.toInt(),
    )

    private var anim: Animation? = null
    private var location: LocationIndicator? = null

    override fun onPause() {
        super.onPause()
        location?.disable()
    }

    override fun onResume() {
        super.onResume()
        location?.enable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val view = findViewById<MapView>(R.id.mapView)
        val controller = view.getController(MapboxMapProvider())
        location = LocationIndicator(controller, this, lifecycleScope).apply {
            setCameraMode(LocationIndicatorCameraMode.Follow())
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.materialToolbar)

        points.first().run {
            controller.addPolyline(
                Polyline.Builder(this)
                    .withWidth(6.0F)
                    .withColor(0xFF0D53FF.toInt())
                    .withBorderWidth(2.0F)
                    .withBorderColor(0xFF1025F5.toInt())
                    .build()
            )
            controller.addMarker(Marker.Builder(first()).build())
            controller.addMarker(Marker.Builder(last()).build())
        }
        points.last().run {
            controller.addPolyline(
                Polyline.Builder(this)
                    .withWidth(6.0F)
                    .withColors(indices.map { colors[it % colors.size] })
                    .withBorderWidth(1.0F)
                    .withBorderColor(0xFF000000.toInt())
                    .build()
            )
            controller.addMarker(Marker.Builder(first()).build())
            controller.addMarker(Marker.Builder(last()).build())
        }

        controller.notifyCameraUpdate(
            CameraUpdateFactory.newCameraUpdateBasedOnBounds(
                points.flatten(),
                120.0F
            )
        )

        toolbar.apply {
            menu.findItem(R.id.enableAnimations).isVisible = true
            menu.findItem(R.id.disableAnimations).isVisible = false

            setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.enableAnimations -> {
                        anim = Animation.Builder().build()
                        menu.findItem(R.id.enableAnimations).isVisible = false
                        menu.findItem(R.id.disableAnimations).isVisible = true
                        true
                    }

                    R.id.disableAnimations -> {
                        anim = null
                        menu.findItem(R.id.enableAnimations).isVisible = true
                        menu.findItem(R.id.disableAnimations).isVisible = false
                        true
                    }

                    R.id.updateCameraPoint -> {
                        MaterialAlertDialogBuilder(context).setTitle(R.string.update_camera_point)
                            .setItems(
                                arrayOf(
                                    "Brandenburg Gate",
                                    "Berlin Wall Memorial",
                                    "East Side Gallery",
                                ),
                            ) { dialog, it ->
                                lifecycleScope.launch {
                                    dialog.dismiss()
                                    delay(200L)
                                    when (it) {
                                        0 -> controller.notifyCameraUpdate(
                                            CameraUpdateFactory.newCameraUpdateBasedOnPoint(
                                                Point(52.516402, 13.379509),
                                            ),
                                            anim
                                        )

                                        1 -> controller.notifyCameraUpdate(
                                            CameraUpdateFactory.newCameraUpdateBasedOnPoint(
                                                Point(52.535116, 13.389691),
                                            ),
                                            anim
                                        )

                                        2 -> controller.notifyCameraUpdate(
                                            CameraUpdateFactory.newCameraUpdateBasedOnPoint(
                                                Point(52.504617, 13.441002),
                                            ),
                                            anim
                                        )
                                    }
                                }
                            }
                            .show()
                        true
                    }

                    R.id.updateCameraBounds -> {
                        controller.notifyCameraUpdate(
                            CameraUpdateFactory.newCameraUpdateBasedOnBounds(
                                points.flatten(),
                                120.0F
                            ),
                            anim
                        )
                        true
                    }

                    R.id.updateCameraBearing -> {
                        val range = 0..360 step 10
                        MaterialAlertDialogBuilder(context).setTitle(R.string.update_camera_bearing)
                            .setItems(
                                range.map { it.toString() }.toTypedArray(),
                            ) { dialog, it ->
                                lifecycleScope.launch {
                                    dialog.dismiss()
                                    delay(200L)
                                    controller.notifyCameraUpdate(
                                        CameraUpdateFactory.newCameraUpdateBearing(
                                            range.elementAt(it).toFloat(),
                                        ),
                                        anim
                                    )
                                }
                            }
                            .show()
                        true
                    }

                    R.id.updateCameraTilt -> {
                        val range = 0..60 step 10
                        MaterialAlertDialogBuilder(context).setTitle(R.string.update_camera_tilt)
                            .setItems(
                                range.map { it.toString() }.toTypedArray(),
                            ) { dialog, it ->
                                lifecycleScope.launch {
                                    dialog.dismiss()
                                    delay(200L)
                                    controller.notifyCameraUpdate(
                                        CameraUpdateFactory.newCameraUpdateTilt(
                                            range.elementAt(it).toFloat(),
                                        ),
                                        anim
                                    )
                                }
                            }
                            .show()
                        true
                    }

                    R.id.updateCameraZoom -> {
                        val range = 0..22 step 1
                        MaterialAlertDialogBuilder(context).setTitle(R.string.update_camera_zoom)
                            .setItems(
                                range.map { it.toString() }.toTypedArray(),
                            ) { dialog, it ->
                                lifecycleScope.launch {
                                    dialog.dismiss()
                                    delay(200L)
                                    controller.notifyCameraUpdate(
                                        CameraUpdateFactory.newCameraUpdateZoom(
                                            range.elementAt(it).toFloat(),
                                        ),
                                        anim
                                    )
                                }
                            }
                            .show()
                        true
                    }

                    else -> false
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}
