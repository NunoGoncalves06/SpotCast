package com.swmad.spotcast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.swmad.spotcast.domain.CustomActivity
import com.swmad.spotcast.domain.Spot
import com.swmad.spotcast.domain.Verdict

// The one confirmation used everywhere a spot can be deleted: the map, the list and the details
@Composable
fun DeleteSpotDialog(spot: Spot, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDeleteDialog(
        title = "Delete ${spot.name}?",
        text = "The spot leaves your map and your list, with its forecast. This cannot be undone.",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

// The same dialog for an activity of your own
@Composable
fun DeleteActivityDialog(activity: CustomActivity, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDeleteDialog(
        title = "Delete ${activity.label}?",
        text = "The activity leaves your activity bar and the report of every spot. This cannot be undone.",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
private fun ConfirmDeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val tint = verdictTint(Verdict.SKIP)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tint.container),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = tint.content)
            }
        },
        title = { Text(title) },
        text = {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep it") }
        }
    )
}
