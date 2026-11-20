package com.swmad.spotcast.integration.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import com.swmad.spotcast.domain.ActivityIcon
import com.swmad.spotcast.domain.Condition
import com.swmad.spotcast.domain.Criterion
import com.swmad.spotcast.domain.CustomActivity
import com.swmad.spotcast.domain.Mark

// Models for handling Firestore data directly, like SpotFS. Every field has a default.
data class CriterionFS(
    var firstChange: Double = 0.0,
    var secondChange: Double = 0.0,
    var marks: List<String> = emptyList()
)

data class ActivityFS(
    @DocumentId var id: String? = null,
    var name: String = "",
    var icon: String = ActivityIcon.FOOTBALL.name,
    var waterOnly: Boolean = false,
    // keyed by the lower case name of the condition: wind, rain, sun, temperature, waves
    var criteria: Map<String, CriterionFS> = emptyMap(),
    // Set by the server when the document is created
    @ServerTimestamp var createdAt: Timestamp? = null
)

fun conditionKey(condition: Condition) = condition.name.lowercase()

fun ActivityFS.toDomain(): CustomActivity {
    val activityId = id ?: throw IllegalStateException("Id invalid")
    return CustomActivity(
        id = activityId,
        label = name,
        icon = ActivityIcon.entries.firstOrNull { it.name == icon } ?: ActivityIcon.FOOTBALL,
        waterOnly = waterOnly,
        // A condition that is missing or damaged falls back to its default, so one bad field never hides the activity
        criteria = Condition.entries.associateWith { condition ->
            criteria[conditionKey(condition)]?.toDomain(condition) ?: condition.defaultCriterion()
        },
        // Not stamped by the server yet: last in the list, and the same value in every snapshot
        createdAt = createdAt?.toDate()?.time ?: Long.MAX_VALUE
    )
}

private fun CriterionFS.toDomain(condition: Condition): Criterion? {
    val parsed = marks.map { name -> Mark.entries.firstOrNull { it.name == name } ?: return null }
    return Criterion(firstChange, secondChange, parsed).takeIf { it.isValid(condition) }
}

fun CustomActivity.toFS(): ActivityFS = ActivityFS(
    name = label.trim(),
    icon = icon.name,
    waterOnly = waterOnly,
    criteria = criteria.entries.associate { (condition, criterion) ->
        conditionKey(condition) to CriterionFS(criterion.first, criterion.second, criterion.marks.map { it.name })
    }
)
