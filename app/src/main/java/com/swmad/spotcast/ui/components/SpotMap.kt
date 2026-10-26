package com.swmad.spotcast.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swmad.spotcast.domain.Verdict
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

data class MapPin(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    // null while the forecast is loading, the pin is then grey
    val verdict: Verdict? = null
)

// OpenFreeMap: free vector tiles from OpenStreetMap data, no API key and no account
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"
private const val SOURCE_ID = "spots"
private const val PIN_LAYER = "spot-pins"
private const val SINGLE_PIN_ZOOM = 14.0
private const val MAX_FIT_ZOOM = 13.5
private const val GROUP_RADIUS_M = 150_000.0

// Where the map starts when there is no spot (Aarhus)
private val DEFAULT_CENTER = LatLng(56.1629, 10.2039)

// The map is drawn in SpotCast's own colours instead of the style's greys
private data class MapPalette(
    val land: Color,
    val landAlt: Color,
    val water: Color,
    val green: Color,
    val building: Color,
    val road: Color,
    val roadEdge: Color,
    val line: Color,
    val label: Color,
    val spotName: Color
)

private val LightPalette = MapPalette(
    land = Color(0xFFEFECE2), landAlt = Color(0xFFE9E5D9), water = Color(0xFFBCDBE6),
    green = Color(0xFFD6E7CF), building = Color(0xFFE2DDD0), road = Color(0xFFFFFDF8),
    roadEdge = Color(0xFFDCD7C9), line = Color(0xFFCFC9BA), label = Color(0xFF5B6A64),
    spotName = Color(0xFF1D2B26)
)

private val DarkPalette = MapPalette(
    land = Color(0xFF161C1A), landAlt = Color(0xFF1A201E), water = Color(0xFF1D3540),
    green = Color(0xFF1C2B23), building = Color(0xFF222927), road = Color(0xFF2E3633),
    roadEdge = Color(0xFF242B29), line = Color(0xFF39413E), label = Color(0xFF9AA7A1),
    spotName = Color(0xFFDEE4E0)
)

// A vector map with MapLibre. MapView is a classic Android view, so it is wrapped in AndroidView.
// The pins are a symbol layer drawn by the map itself, so they stay sharp and move with it.
@Composable
fun SpotMap(
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    // false gives a still picture: the map ignores touches, so a scrolling page keeps scrolling
    interactive: Boolean = true,
    // Space covered by a sheet at the bottom, so the selected pin is moved above it
    bottomInset: Dp = 0.dp,
    onPinClick: (String) -> Unit = {},
    onPinLongClick: (String) -> Unit = {},
    onMapClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val currentOnPinClick by rememberUpdatedState(onPinClick)
    val currentOnPinLongClick by rememberUpdatedState(onPinLongClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    // The camera fits the pins once, after that the user is in control
    var placed by remember { mutableStateOf(false) }

    val mapView = remember {
        MapLibre.getInstance(context)
        // TextureView mode, so the map can be clipped to rounded corners like any composable
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        MapView(context, options).apply {
            getMapAsync { loaded ->
                loaded.uiSettings.isRotateGesturesEnabled = false
                loaded.uiSettings.isTiltGesturesEnabled = false
                loaded.uiSettings.isCompassEnabled = false
                loaded.uiSettings.setAllGesturesEnabled(interactive)
                loaded.cameraPosition = CameraPosition.Builder().target(DEFAULT_CENTER).zoom(10.0).build()
                fun pinAt(point: LatLng): String? {
                    val screen = loaded.projection.toScreenLocation(point)
                    return loaded.queryRenderedFeatures(screen, PIN_LAYER).firstOrNull()?.getStringProperty("id")
                }
                loaded.addOnMapClickListener { point ->
                    val id = pinAt(point)
                    if (id != null) currentOnPinClick(id) else currentOnMapClick()
                    true
                }
                // A long press on a pin asks to delete the spot; elsewhere the map keeps the gesture
                loaded.addOnMapLongClickListener { point ->
                    val id = pinAt(point) ?: return@addOnMapLongClickListener false
                    currentOnPinLongClick(id)
                    true
                }
                loaded.setStyle(Style.Builder().fromUri(STYLE_URL)) { ready ->
                    addPinImages(ready, context)
                    ready.addSource(GeoJsonSource(SOURCE_ID))
                    ready.addLayer(pinLayer())
                    style = ready
                }
                map = loaded
            }
        }
    }

    LaunchedEffect(style, dark) {
        style?.let { recolor(it, if (dark) DarkPalette else LightPalette) }
    }

    LaunchedEffect(style, pins, selectedId) {
        val ready = style ?: return@LaunchedEffect
        ready.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(featureCollection(pins, selectedId))
    }

    LaunchedEffect(map, pins) {
        val loaded = map ?: return@LaunchedEffect
        if (placed || pins.isEmpty()) return@LaunchedEffect
        placed = true
        loaded.moveCamera(CameraUpdateFactory.newCameraPosition(fitCamera(loaded, pins, density.density)))
    }

    // A selected pin glides to the middle of the space above the sheet
    LaunchedEffect(map, selectedId) {
        val loaded = map ?: return@LaunchedEffect
        val pin = pins.firstOrNull { it.id == selectedId } ?: return@LaunchedEffect
        val bottom = with(density) { bottomInset.toPx() }.toDouble()
        loaded.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(pin.latitude, pin.longitude))
                    .zoom(maxOf(loaded.cameraPosition.zoom, 12.0))
                    .padding(0.0, 0.0, 0.0, bottom)
                    .build()
            ),
            450
        )
    }

    // The view has to follow the lifecycle and be destroyed when it leaves the composition
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        if (!interactive) {
            // An empty pointerInput on top takes the touches before the map does
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {}
            )
        }
    }
}

private fun fitCamera(map: MapLibreMap, all: List<MapPin>, density: Float): CameraPosition {
    // Fits the biggest group of spots, so one spot on another continent does not zoom out to the world
    val points = all.map { LatLng(it.latitude, it.longitude) }
    val home = points.maxBy { p -> points.count { it.distanceTo(p) < GROUP_RADIUS_M } }
    val group = points.filter { it.distanceTo(home) < GROUP_RADIUS_M }
    if (group.size == 1) return CameraPosition.Builder().target(home).zoom(SINGLE_PIN_ZOOM).build()
    val bounds = LatLngBounds.Builder().includes(group).build()
    // Room for the activity bar on top, the sheet below and the pins' own height
    val side = (48 * density).toInt()
    val fitted = map.getCameraForLatLngBounds(
        bounds,
        intArrayOf(side, (210 * density).toInt(), side, (120 * density).toInt())
    )
    return if (fitted == null || fitted.zoom > MAX_FIT_ZOOM) {
        CameraPosition.Builder().target(bounds.center).zoom(MAX_FIT_ZOOM).build()
    } else {
        fitted
    }
}

private fun featureCollection(pins: List<MapPin>, selectedId: String?): FeatureCollection =
    FeatureCollection.fromFeatures(pins.map { pin ->
        Feature.fromGeometry(Point.fromLngLat(pin.longitude, pin.latitude)).apply {
            addStringProperty("id", pin.id)
            addStringProperty("name", pin.name)
            addStringProperty("pin", pinImageName(pin.verdict))
            addBooleanProperty("selected", pin.id == selectedId)
        }
    })

private fun pinLayer(): SymbolLayer {
    val selected = Expression.toBool(Expression.get("selected"))
    return SymbolLayer(PIN_LAYER, SOURCE_ID).withProperties(
        PropertyFactory.iconImage(Expression.get("pin")),
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        // The bitmap has room for its shadow under the tip
        PropertyFactory.iconOffset(arrayOf(0f, PIN_PADDING_DP)),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
        PropertyFactory.iconSize(
            Expression.switchCase(selected, Expression.literal(1.3f), Expression.literal(1f))
        ),
        // The selected pin is drawn on top of its neighbours
        PropertyFactory.symbolSortKey(
            Expression.switchCase(selected, Expression.literal(1f), Expression.literal(0f))
        ),
        // Names under the pins; a name that would collide with another one is left out
        PropertyFactory.textField(Expression.get("name")),
        PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
        PropertyFactory.textSize(12f),
        PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
        PropertyFactory.textOffset(arrayOf(0f, 0.4f)),
        PropertyFactory.textOptional(true),
        PropertyFactory.textMaxWidth(8f)
    )
}

// Recolours the layers of the Positron style by their ids
private fun recolor(style: Style, palette: MapPalette) {
    val water = Regex("water")
    val green = Regex("park|wood|grass")
    val road = Regex("highway|road|tunnel|bridge")
    for (layer in style.layers) {
        val id = layer.id
        when (layer) {
            is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(palette.land.toArgb()))

            is FillLayer -> layer.setProperties(
                PropertyFactory.fillColor(
                    when {
                        water.containsMatchIn(id) -> palette.water
                        green.containsMatchIn(id) -> palette.green
                        id.startsWith("building") -> palette.building
                        id.startsWith("landuse") -> palette.landAlt
                        else -> palette.land
                    }.toArgb()
                ),
                PropertyFactory.fillOutlineColor(palette.land.toArgb())
            )

            is LineLayer -> layer.setProperties(
                PropertyFactory.lineColor(
                    when {
                        water.containsMatchIn(id) -> palette.water
                        road.containsMatchIn(id) && id.contains("casing") -> palette.roadEdge
                        road.containsMatchIn(id) -> palette.road
                        else -> palette.line
                    }.toArgb()
                )
            )

            is SymbolLayer -> layer.setProperties(
                PropertyFactory.textColor((if (id == PIN_LAYER) palette.spotName else palette.label).toArgb()),
                PropertyFactory.textHaloColor(palette.land.toArgb()),
                PropertyFactory.textHaloWidth(1.5f)
            )
        }
    }
}

private const val PIN_WIDTH_DP = 30f
private const val PIN_HEIGHT_DP = 40f
private const val PIN_PADDING_DP = 4f

private fun pinImageName(verdict: Verdict?) = "pin-${verdict?.name ?: "NONE"}"

private fun addPinImages(style: Style, context: Context) {
    (Verdict.entries + listOf(null)).forEach { verdict ->
        style.addImage(pinImageName(verdict), pinBitmap(context, verdictColor(verdict).toArgb()))
    }
}

// The teardrop pin: a coloured drop with a white rim and a white dot, and a soft shadow
private fun pinBitmap(context: Context, color: Int): Bitmap {
    val d = context.resources.displayMetrics.density
    val pad = PIN_PADDING_DP * d
    val bitmap = Bitmap.createBitmap(
        (PIN_WIDTH_DP * d + 2 * pad).toInt(),
        (PIN_HEIGHT_DP * d + 2 * pad).toInt(),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    canvas.translate(pad, pad)
    // The drawing is made on a 24 x 32 grid and scaled to the pin size
    canvas.scale(PIN_WIDTH_DP * d / 24f, PIN_HEIGHT_DP * d / 32f)
    val drop = Path().apply {
        moveTo(12f, 31f)
        cubicTo(12f, 31f, 1f, 18f, 1f, 11f)
        arcTo(RectF(1f, 0f, 23f, 22f), 180f, 180f, false)
        cubicTo(23f, 18f, 12f, 31f, 12f, 31f)
        close()
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        setShadowLayer(1.6f, 0f, 0.8f, 0x55000000)
    }
    canvas.drawPath(drop, fill)
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    canvas.drawPath(drop, rim)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE }
    canvas.drawCircle(12f, 11f, 4.2f, dot)
    return bitmap
}
