package com.kehuiai.data

/**
 * Tag suggestion for prompt autocomplete
 */
data class TagSuggestion(
    val replacementTag: String,
    val primaryText: String,
    val secondaryText: String? = null,
    val category: Int = 0,
    val postCount: Int = 0,
    val matchType: TagMatchType = TagMatchType.Tag
)

enum class TagMatchType {
    Tag,
    Alias,
    Correction,
    Embedding,
}
