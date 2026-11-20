package com.swmad.spotcast.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swmad.spotcast.domain.ActivityIcon
import com.swmad.spotcast.domain.ActivityPlace
import com.swmad.spotcast.domain.Condition
import com.swmad.spotcast.domain.Criterion
import com.swmad.spotcast.domain.Mark
import com.swmad.spotcast.domain.Rating
import com.swmad.spotcast.domain.Verdict
import com.swmad.spotcast.ui.components.DeleteActivityDialog
import com.swmad.spotcast.ui.components.customActivityIcon
import com.swmad.spotcast.ui.components.verdictTint
import com.swmad.spotcast.ui.spots.SpotsViewModel
import java.util.Locale

// Makes a new activity, or edits one of your own. The built-in activities never open this screen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityEditorScreen(
    activityId: String?,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    spotsViewModel: SpotsViewModel,
    editor: ActivityEditorViewModel = viewModel()
) {
    val existing = activityId?.let { id -> spotsViewModel.customActivities.firstOrNull { it.id == id } }
    var showDelete by rememberSaveable { mutableStateOf(false) }

    // An activity that is being edited is looked up in the live list, so the form waits until it is there.
    // A message from an earlier screen is cleared once, not again after a rotation.
    LaunchedEffect(activityId, existing) {
        if (!editor.started) spotsViewModel.clearActivityError()
        if (activityId == null || existing != null) editor.start(existing)
    }
    // The list has loaded and the activity is not in it: it was deleted somewhere else, so there is nothing to edit
    LaunchedEffect(activityId, existing, spotsViewModel.activitiesReady) {
        if (!editor.started && activityId != null && existing == null && spotsViewModel.activitiesReady) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editor.isNew) "New activity" else "Edit activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!editor.isNew) {
                        IconButton(onClick = { showDelete = true }, enabled = !spotsViewModel.isSavingActivity) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete ${editor.name}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!editor.started) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = editor::rename,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Icon", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActivityIcon.entries.forEach { item ->
                        FilledTonalIconToggleButton(
                            checked = item == editor.icon,
                            onCheckedChange = { editor.chooseIcon(item) }
                        ) {
                            Icon(customActivityIcon(item), contentDescription = item.label)
                        }
                    }
                }

                // Only one of these can be on: an activity is for one kind of spot
                Text("Where it can happen", style = MaterialTheme.typography.labelLarge)
                Column {
                    ActivityPlace.entries.filter { it != ActivityPlace.ANYWHERE }.forEach { option ->
                        val selected = editor.place == option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .toggleable(
                                    value = selected,
                                    role = Role.Switch,
                                    onValueChange = { on -> editor.choosePlace(if (on) option else ActivityPlace.ANYWHERE) }
                                )
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = selected, onCheckedChange = null)
                        }
                    }
                }

                if (editor.place == ActivityPlace.INDOORS) {
                    // The conditions are kept (the database needs them) but they do not count indoors
                    Text(
                        text = "Indoors the weather does not matter, so there are no conditions to set. " +
                            "You only get a warning when the weather is very bad.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                Text("How each condition counts", style = MaterialTheme.typography.labelLarge)
                editor.conditions.forEach { condition ->
                    ConditionCard(
                        condition = condition,
                        criterion = editor.criteria.getValue(condition),
                        adjusting = condition in editor.adjusting,
                        onMark = { range -> editor.cycleMark(condition, range) },
                        onAdjust = { editor.toggleAdjust(condition) },
                        onFirst = { editor.moveFirst(condition, it) },
                        onSecond = { editor.moveSecond(condition, it) },
                        onReset = { editor.resetRanges(condition) }
                    )
                }

                Text("Try it on a day", style = MaterialTheme.typography.labelLarge)
                editor.conditions.forEach { condition ->
                    NumberSlider(
                        label = condition.label,
                        condition = condition,
                        value = editor.sample.getValue(condition),
                        onChange = { editor.moveSample(condition, it) }
                    )
                }
                VerdictTile(editor.preview)
                }

                spotsViewModel.activityError?.let { message ->
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = { spotsViewModel.saveActivity(editor.build(), onDone = onFinished) },
                    enabled = editor.name.isNotBlank() && !spotsViewModel.isSavingActivity,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (spotsViewModel.isSavingActivity) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save activity")
                    }
                }
            }
        }
    }

    if (showDelete) {
        existing?.let { activity ->
            DeleteActivityDialog(
                activity = activity,
                onConfirm = {
                    showDelete = false
                    spotsViewModel.deleteActivity(activity.id, onDone = onFinished)
                },
                onDismiss = { showDelete = false }
            )
        }
    }
}

// One condition with its three ranges. A tap on a range cycles its mark, Adjust opens the two sliders.
@Composable
private fun ConditionCard(
    condition: Condition,
    criterion: Criterion,
    adjusting: Boolean,
    onMark: (Int) -> Unit,
    onAdjust: () -> Unit,
    onFirst: (Double) -> Unit,
    onSecond: (Double) -> Unit,
    onReset: () -> Unit
) {
    val changed = criterion.first != condition.defaultFirst || criterion.second != condition.defaultSecond
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(condition.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = condition.unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
                if (changed) {
                    val tint = verdictTint(Verdict.GO)
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.labelSmall,
                        color = tint.content,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(tint.container)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAdjust) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (adjusting) "Done" else "Adjust")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                condition.ranges.forEachIndexed { index, name ->
                    MarkChip(
                        name = name,
                        hint = rangeHint(condition, criterion, index),
                        mark = criterion.marks[index],
                        onClick = { onMark(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (adjusting) {
                NumberSlider("First change", condition, criterion.first, onFirst)
                NumberSlider("Second change", condition, criterion.second, onSecond)
                TextButton(onClick = onReset) { Text("Reset to default") }
            }
        }
    }
}

// The mark is written out as well as coloured, so it does not depend on colour alone
@Composable
private fun MarkChip(name: String, hint: String, mark: Mark, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = verdictTint(mark.verdict)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.container)
            .clickable(onClickLabel = "Change how $name counts", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = "$hint, ${mark.label}" }
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = tint.content
        )
        Text(
            text = "$hint \u00B7 ${mark.label}",
            style = MaterialTheme.typography.labelSmall,
            color = tint.content
        )
    }
}

@Composable
private fun NumberSlider(label: String, condition: Condition, value: Double, onChange: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = condition.min.toFloat()..condition.max.toFloat(),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "$label, ${condition.label}" }
        )
        Text(
            text = condition.format(value),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(84.dp)
        )
    }
}

// What the activity would say on the day above, in the colours of the report card
@Composable
private fun VerdictTile(rating: Rating) {
    val tint = verdictTint(rating.verdict)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tint.container)
            .padding(14.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.content.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (rating.verdict) {
                    Verdict.GO -> Icons.Filled.Check
                    Verdict.MAYBE -> Icons.Filled.Warning
                    Verdict.SKIP -> Icons.Filled.Close
                },
                contentDescription = null,
                tint = tint.content
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(rating.title, style = MaterialTheme.typography.titleMedium, color = tint.content)
            Text(rating.reason, style = MaterialTheme.typography.bodyMedium, color = tint.content)
        }
    }
}

private fun rangeHint(condition: Condition, criterion: Criterion, index: Int): String {
    fun number(value: Double) = if (condition.step < 1) "%.1f".format(Locale.US, value) else value.toInt().toString()
    return when (index) {
        0 -> "under ${number(criterion.first)}"
        1 -> "${number(criterion.first)} to ${number(criterion.second)}"
        else -> "${number(criterion.second)} and up"
    }
}
