package com.swmad.spotcast.ui.map

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationServices
import com.swmad.spotcast.integration.location.LOCATION_PERMISSIONS
import com.swmad.spotcast.integration.location.LocationService
import com.swmad.spotcast.integration.location.hasLocationPermission
import com.swmad.spotcast.ui.components.UserPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swmad.spotcast.domain.ActivityKind
import com.swmad.spotcast.domain.CustomActivity
import com.swmad.spotcast.domain.MAX_CUSTOM_ACTIVITIES
import com.swmad.spotcast.domain.DailyForecast
import com.swmad.spotcast.domain.Rating
import com.swmad.spotcast.domain.Spot
import com.swmad.spotcast.domain.Verdict
import com.swmad.spotcast.domain.bestRating
import com.swmad.spotcast.domain.rate
import com.swmad.spotcast.domain.rateAll
import com.swmad.spotcast.ui.components.DeleteSpotDialog
import com.swmad.spotcast.ui.components.MapPin
import com.swmad.spotcast.ui.components.SpotMap
import com.swmad.spotcast.ui.components.activityIcon
import com.swmad.spotcast.ui.components.verdictColor
import com.swmad.spotcast.ui.components.verdictTint
import com.swmad.spotcast.ui.components.weatherIcon
import com.swmad.spotcast.ui.details.WeatherUiState
import com.swmad.spotcast.ui.spots.SpotsViewModel
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

// The rating that colours a pin: the chosen activity, or the spot's best one for "Best today"
fun ratingFor(spot: Spot, day: DailyForecast, activity: ActivityKind?, activities: List<ActivityKind>): Rating =
    if (activity == null) bestRating(day, spot.category, activities) else rate(activity, day, spot.category)

private fun today(state: WeatherUiState?): DailyForecast? =
    (state as? WeatherUiState.Loaded)?.weather?.daily?.firstOrNull()

private const val DRAFT_ID = "draft"
private const val TAG = "MAP SCREEN"

// The home screen: every spot on the map, coloured by whether today is a Go for the chosen activity
@Composable
fun MapScreen(
    onOpenSpot: (String) -> Unit,
    onAddSpot: () -> Unit,
    onAddSpotAt: (latitude: Double, longitude: Double) -> Unit,
    onNewActivity: () -> Unit,
    onEditActivity: (String) -> Unit,
    viewModel: SpotsViewModel = viewModel()
) {
    val spots = viewModel.spots
    val activity = viewModel.activity
    val selected = spots.firstOrNull { it.id == viewModel.selectedSpotId }
    val density = LocalDensity.current
    var sheetHeight by remember { mutableStateOf(320.dp) }
    // Only the id is kept, so the dialog survives a rotation. The spot is looked up in the list.
    var toDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val toDelete = spots.firstOrNull { it.id == toDeleteId }
    val snackbarHost = remember { SnackbarHostState() }
    var firstCardHeight by remember { mutableStateOf(0.dp) }

    // A point the user tapped on the empty map, waiting for "Add spot". NaN means no point.
    var draftLatitude by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    var draftLongitude by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    val draft = if (draftLatitude.isNaN() || draftLongitude.isNaN()) null else draftLatitude to draftLongitude
    fun clearDraft() {
        draftLatitude = Double.NaN
        draftLongitude = Double.NaN
    }

    // The "My location" button
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationService = remember { LocationService(LocationServices.getFusedLocationProviderClient(context)) }
    val servicesOn by rememberLocationServicesOn()
    var locating by remember { mutableStateOf(false) }
    // Two doubles like the draft, so the blue dot survives a rotation. NaN means no position yet.
    var myLatitude by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    var myLongitude by rememberSaveable { mutableDoubleStateOf(Double.NaN) }
    val myPosition = if (myLatitude.isNaN() || myLongitude.isNaN()) null else UserPosition(myLatitude, myLongitude)
    var recenterToken by remember { mutableIntStateOf(0) }

    // A snackbar with a Settings button, for what only the phone's settings can fix
    fun offerSettings(message: String, settings: Intent) {
        scope.launch {
            val result = snackbarHost.showSnackbar(
                message = message,
                actionLabel = "Settings",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) context.startActivity(settings)
        }
    }

    fun showMyPosition() {
        if (locating) return
        scope.launch {
            locating = true
            val position = try {
                locationService.getCurrentLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.v(TAG, e.toString())
                null
            }
            locating = false
            if (position == null) {
                snackbarHost.showSnackbar("The position could not be read")
            } else {
                myLatitude = position.latitude
                myLongitude = position.longitude
                recenterToken++
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result: Map<String, Boolean> ->
        if (result.values.any { it }) {
            showMyPosition()
        } else {
            // After two refusals Android no longer asks, so the button would do nothing
            offerSettings(
                "Location permission is needed",
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }
    }

    // A failed delete is a message over the map. The list and the pins stay as they are.
    // The message is cleared after it was shown: clearing it first would restart this effect and cancel the snackbar.
    LaunchedEffect(viewModel.deleteError) {
        val message = viewModel.deleteError ?: return@LaunchedEffect
        snackbarHost.showSnackbar("The spot could not be deleted: $message")
        viewModel.clearDeleteError()
    }

    // System Back closes the sheet before it leaves the screen
    BackHandler(enabled = selected != null) { viewModel.selectSpot(null) }
    BackHandler(enabled = draft != null) { clearDraft() }

    val pins = spots.map { spot ->
        MapPin(
            id = spot.id,
            latitude = spot.latitude,
            longitude = spot.longitude,
            name = spot.name,
            verdict = today(viewModel.forecasts[spot.id])?.let { ratingFor(spot, it, activity, viewModel.activities).verdict }
        )
    }
    // The tapped point is drawn as a grey pin until the user adds the spot or taps elsewhere
    val mapPins = pins + listOfNotNull(draft?.let { MapPin(DRAFT_ID, it.first, it.second, "New spot") })
    val showFirstCard = !viewModel.isLoading && spots.isEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SpotMap(
            pins = mapPins,
            selectedId = selected?.id,
            bottomInset = sheetHeight,
            onPinClick = { id ->
                if (id == DRAFT_ID) {
                    draft?.let { (latitude, longitude) ->
                        clearDraft()
                        onAddSpotAt(latitude, longitude)
                    }
                } else {
                    clearDraft()
                    viewModel.selectSpot(id)
                }
            },
            onPinLongClick = { id -> toDeleteId = id },
            // An open sheet is closed by a tap on the map; with no sheet, the tap chooses a place for a new spot
            onMapClick = { latitude, longitude ->
                if (selected != null) {
                    viewModel.selectSpot(null)
                } else {
                    draftLatitude = latitude
                    draftLongitude = longitude
                }
            },
            userPosition = myPosition,
            recenterToken = recenterToken,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ActivityBar(
                activities = viewModel.activities,
                selected = activity,
                canAdd = viewModel.canAddActivity,
                onSelect = { viewModel.selectActivity(it) },
                onNew = {
                    if (viewModel.canAddActivity) {
                        onNewActivity()
                    } else {
                        scope.launch {
                            snackbarHost.showSnackbar(
                                "You can have up to $MAX_CUSTOM_ACTIVITIES activities of your own. Delete one to add another."
                            )
                        }
                    }
                },
                onEdit = { onEditActivity(it.id) }
            )
            if (spots.isNotEmpty()) {
                val states = spots.map { viewModel.forecasts[it.id] }
                val summary = mapSummary(
                    total = spots.size,
                    goes = pins.count { it.verdict == Verdict.GO },
                    // a spot without an entry has not started loading yet
                    loading = states.count { it == null || it is WeatherUiState.Loading },
                    failed = states.count { it is WeatherUiState.Error },
                    activity = activity
                )
                SummaryPill(
                    summary,
                    onRetry = { viewModel.retryFailedForecasts() },
                    modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp)
                )
            }
        }

        if (showFirstCard) {
            FirstSpotCard(
                message = viewModel.error,
                onAddSpot = onAddSpot,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .onSizeChanged { firstCardHeight = with(density) { it.height.toDp() } + 12.dp }
            )
        }

        // My location and Add spot, above the card or the sheet when there is one
        val overlayBottom = when {
            selected != null -> sheetHeight
            showFirstCard -> firstCardHeight
            else -> 0.dp
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp + overlayBottom)
        ) {
            MyLocationButton(
                servicesOn = servicesOn,
                locating = locating,
                onClick = {
                    when {
                        !servicesOn -> offerSettings(
                            "Location is turned off",
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        )

                        !hasLocationPermission(context) -> permissionLauncher.launch(LOCATION_PERMISSIONS)

                        else -> showMyPosition()
                    }
                }
            )
            if (!showFirstCard && selected == null && draft == null) {
                ExtendedFloatingActionButton(
                    onClick = onAddSpot,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add spot") }
                )
            }
        }

        if (draft != null && selected == null) {
            AddHerePopup(
                onAdd = {
                    clearDraft()
                    onAddSpotAt(draft.first, draft.second)
                },
                onDismiss = { clearDraft() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }

        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // The last spot is kept while the sheet slides away
            val shown = remember { mutableStateOf<Spot?>(null) }
            if (selected != null) shown.value = selected
            val spot = shown.value ?: return@AnimatedVisibility
            SpotSheet(
                spot = spot,
                state = viewModel.forecasts[spot.id],
                activity = activity,
                activities = viewModel.activities,
                onSwitch = { viewModel.selectActivity(it) },
                onOpenDetails = { onOpenSpot(spot.id) },
                onRetry = { viewModel.retryForecast(spot) },
                onClose = { viewModel.selectSpot(null) },
                onDelete = { toDeleteId = spot.id },
                modifier = Modifier
                    .padding(10.dp)
                    .onSizeChanged { sheetHeight = with(density) { it.height.toDp() } + 10.dp }
            )
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 148.dp)
        )
    }

    toDelete?.let { spot ->
        DeleteSpotDialog(
            spot = spot,
            onConfirm = {
                viewModel.deleteSpot(spot)
                toDeleteId = null
            },
            onDismiss = { toDeleteId = null }
        )
    }
}

// A long press on something that is also clickable. The chip handles the touch itself, so this listens before
// the chip does (Initial pass) and swallows the release of a long press, so the chip is not selected as well.
// A drag that scrolls the bar cancels it.
private fun Modifier.onLongPress(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val timedOut = try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation(PointerEventPass.Initial) }
            false
        } catch (e: PointerEventTimeoutCancellationException) {
            true
        }
        if (timedOut) {
            onLongPress()
            waitForUpOrCancellation(PointerEventPass.Initial)?.consume()
        }
    }
}

// The small pop up over a tapped point of the map
@Composable
private fun AddHerePopup(onAdd: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
            TextButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add spot")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel")
            }
        }
    }
}

@Composable
private fun ActivityBar(
    activities: List<ActivityKind>,
    selected: ActivityKind?,
    canAdd: Boolean,
    onSelect: (ActivityKind?) -> Unit,
    onNew: () -> Unit,
    onEdit: (CustomActivity) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        val options = listOf<ActivityKind?>(null) + activities
        val listState = rememberLazyListState()
        // A switch from the sheet can pick a chip that is scrolled away, so bring it into view
        LaunchedEffect(selected) {
            listState.animateScrollToItem(maxOf(0, options.indexOf(selected) - 1))
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(options, key = { it?.id ?: "best" }) { option ->
                val isSelected = option == selected
                // An own activity opens its editor on a long press. The built-in ones cannot be changed.
                val editable = (option as? CustomActivity)?.let { custom ->
                    Modifier
                        .onLongPress { onEdit(custom) }
                        .semantics { onLongClick(label = "Edit ${custom.label}") { onEdit(custom); true } }
                } ?: Modifier
                FilterChip(
                    modifier = editable,
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = { Text(option?.label ?: "Best today") },
                    leadingIcon = {
                        Icon(activityIcon(option), contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        iconColor = MaterialTheme.colorScheme.primary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = Color.Transparent
                    )
                )
            }
            item(key = "new") {
                FilterChip(
                    selected = false,
                    onClick = onNew,
                    label = { Text(if (canAdd) "New activity" else "Limit reached") },
                    leadingIcon = {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = MaterialTheme.colorScheme.primary,
                        iconColor = MaterialTheme.colorScheme.primary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun SummaryPill(summary: MapSummary, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
        modifier = if (summary.canRetry) {
            // a tap target of the minimum size, so the pill can be hit with a thumb
            modifier.minimumInteractiveComponentSize().clip(CircleShape).clickable(onClick = onRetry)
        } else {
            modifier
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (summary.checking) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(verdictColor(summary.dot))
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(summary.text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FirstSpotCard(message: String?, onAddSpot: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Save your first spot", style = MaterialTheme.typography.titleLarge)
            Text(
                text = message ?: "Add the beaches, trails and viewpoints you like. " +
                    "Every day SpotCast tells you where to go and what to do there.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAddSpot, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add spot")
            }
        }
    }
}

@Composable
private fun SpotSheet(
    spot: Spot,
    state: WeatherUiState?,
    activity: ActivityKind?,
    activities: List<ActivityKind>,
    onSwitch: (ActivityKind) -> Unit,
    onOpenDetails: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            when (state) {
                is WeatherUiState.Loaded -> {
                    val days = state.weather.daily
                    val day = days.firstOrNull()
                    if (day == null) {
                        SheetHeader(spot, null, "No forecast for today", onClose)
                    } else {
                        Report(spot, day, days, activity, activities, onSwitch, onClose)
                    }
                }

                is WeatherUiState.Error -> {
                    SheetHeader(spot, null, "No forecast right now", onClose)
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Try again") }
                }

                else -> {
                    SheetHeader(spot, null, "Checking the forecast", onClose)
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp).size(24.dp))
                }
            }
            Row(
                modifier = Modifier.padding(top = 12.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onOpenDetails, modifier = Modifier.weight(1f)) {
                    Text("Full forecast and details")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedIconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete ${spot.name}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(spot: Spot, rating: Rating?, title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val tint = rating?.let { verdictTint(it.verdict) }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint?.container ?: MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = activityIcon(rating?.activity),
                contentDescription = null,
                tint = tint?.content ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = spot.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
        }
    }
}

@Composable
private fun Report(
    spot: Spot,
    day: DailyForecast,
    days: List<DailyForecast>,
    activity: ActivityKind?,
    activities: List<ActivityKind>,
    onSwitch: (ActivityKind) -> Unit,
    onClose: () -> Unit
) {
    val rating = ratingFor(spot, day, activity, activities)
    val all = rateAll(day, spot.category, activities)
    SheetHeader(spot, rating, rating.title, onClose)

    Column(modifier = Modifier.padding(end = 10.dp)) {
        Text(
            text = rating.reason,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 10.dp)
        )
        Conditions(day)

        // Something else is better here today: offer to switch to it
        val better = all.filter { it.verdict > rating.verdict }.maxByOrNull { it.verdict }
        if (activity != null && better != null) {
            Suggestion(better, onSwitch = { onSwitch(better.activity) })
        }

        Text(
            text = "Everything here today",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
        )
        TodayRow(all = all, current = rating.activity, onSwitch = onSwitch)

        WeekStrip(spot = spot, days = days, activity = activity ?: rating.activity)
    }
}

@Composable
private fun Conditions(day: DailyForecast) {
    val parts = buildList {
        day.maxTemperature?.let { add("${it.roundToInt()} °C") }
        day.precipitationProbability?.let { add("$it% rain") }
        day.maxWindSpeed?.let { add("${it.roundToInt()} km/h wind") }
        day.waveHeight?.let { add("${"%.1f".format(Locale.US, it)} m waves") }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Icon(
            imageVector = weatherIcon(day.weatherCode),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = parts.joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Suggestion(better: Rating, onSwitch: () -> Unit) {
    val tint = verdictTint(better.verdict)
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tint.container)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(activityIcon(better.activity), contentDescription = null, tint = tint.content)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = if (better.verdict == Verdict.GO) better.activity.suggestion else better.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tint.content
            )
            Text(better.reason, style = MaterialTheme.typography.bodySmall, color = tint.content)
        }
        Button(onClick = onSwitch) { Text("Switch") }
    }
}

// One icon per activity, coloured by today's verdict. Tapping one switches the whole map to it.
@Composable
private fun TodayRow(all: List<Rating>, current: ActivityKind, onSwitch: (ActivityKind) -> Unit) {
    // Seven built-in activities and more of your own: the icons keep a fixed width and the row scrolls
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        all.forEach { rating ->
            val tint = verdictTint(rating.verdict)
            val isCurrent = rating.activity == current
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tint.container)
                    .clickable { onSwitch(rating.activity) }
                    .semantics { contentDescription = "${rating.activity.label}: ${rating.title}" },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = activityIcon(rating.activity),
                        contentDescription = null,
                        tint = tint.content,
                        modifier = Modifier.size(20.dp)
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(width = 14.dp, height = 3.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent) tint.content else Color.Transparent)
                    )
                }
            }
        }
    }
}

// The next 7 days for one activity, so a Skip today can point to a better day
@Composable
private fun WeekStrip(spot: Spot, days: List<DailyForecast>, activity: ActivityKind) {
    val ratings = days.map { rate(activity, it, spot.category) }
    val bestDay = days.indices.firstOrNull { ratings[it].verdict == Verdict.GO }
    val note = when (bestDay) {
        null -> "No Go day for ${activity.purpose} this week"
        0 -> "Today is a Go for ${activity.purpose}"
        else -> "Best day for ${activity.purpose}: " +
            days[bestDay].date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    }
    Text(
        text = note,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { index, day ->
            val tint = verdictTint(ratings[index].verdict)
            Text(
                text = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (index == bestDay) FontWeight.Bold else FontWeight.Normal,
                color = tint.content,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.container)
                    .padding(vertical = 6.dp)
                    .semantics { contentDescription = "${day.date.dayOfWeek}: ${ratings[index].title}" }
            )
        }
    }
}
