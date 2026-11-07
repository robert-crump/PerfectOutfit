package com.example.perfectoutfit.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class BodyPart(val displayName: String) {
    HEAD_THROAT("Head and Throat"),
    UPPER_BODY("Upper Body"),
    ARMS("Arms"),
    LEGS("Legs"),
    FEET("Feet"),
    HANDS("Hands")
}

/**
 * Head to toe, with hands after the arms they hang from. Applied only by [headToToeGroups] and
 * [inHeadToToeOrder]; nothing else orders by body part.
 */
private val BODY_PART_DISPLAY_ORDER = listOf(
    BodyPart.HEAD_THROAT,
    BodyPart.UPPER_BODY,
    BodyPart.ARMS,
    BodyPart.HANDS,
    BodyPart.LEGS,
    BodyPart.FEET
)

/**
 * Groups elements by body part in head-to-toe order, keeping each group's incoming order.
 * Body parts with no elements are skipped unless [includeEmpty].
 */
fun <T> Iterable<T>.headToToeGroups(
    includeEmpty: Boolean = false,
    bodyPartOf: (T) -> BodyPart
): List<Pair<BodyPart, List<T>>> {
    val byPart = groupBy(bodyPartOf)
    return BODY_PART_DISPLAY_ORDER.mapNotNull { part ->
        val group = byPart[part].orEmpty()
        if (group.isEmpty() && !includeEmpty) null else part to group
    }
}

/** Clothing items in display order: head to toe, then by name. */
fun Iterable<ClothingItem>.inHeadToToeOrder(): List<ClothingItem> =
    headToToeGroups { it.bodyPart }.flatMap { (_, items) -> items.sortedBy { it.name } }

/** Every body part, head to toe (for pickers). */
val ALL_BODY_PARTS_HEAD_TO_TOE: List<BodyPart> get() = BODY_PART_DISPLAY_ORDER
