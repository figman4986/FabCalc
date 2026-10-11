package com.figapps.fractioncalculatorv1.wire

/** A metal-type group of rows, ready for the browse list. */
data class WireGroup(val metalType: MetalType, val entries: List<WireDatasheet>)

/**
 * Pure, Android-free filtering and sorting for the wire library: a search string plus single-select
 * metal-type and process filters, applied over a list of [WireDatasheet]s.
 */
data class WireLibraryFilter(
    val searchText: String = "",
    val metalType: MetalType? = null,
    val process: WireProcess? = null,
) {
    fun matches(datasheet: WireDatasheet): Boolean {
        if (metalType != null && datasheet.metalType != metalType) return false
        if (process != null && datasheet.process != process) return false
        val query = searchText.trim()
        if (query.isEmpty()) return true
        return datasheet.brand.contains(query, ignoreCase = true) ||
            datasheet.productName.contains(query, ignoreCase = true) ||
            datasheet.awsClassification.contains(query, ignoreCase = true)
    }

    /** Filtered rows, sorted by metal type then wire size (unknown sizes last). */
    fun apply(source: List<WireDatasheet>): List<WireDatasheet> = sortForLibrary(source.filter(::matches))

    companion object {
        /** Ascending metal type (enum order), then ascending size, then product name. */
        fun sortForLibrary(source: List<WireDatasheet>): List<WireDatasheet> =
            source.sortedWith(
                compareBy<WireDatasheet> { it.metalType.ordinal }
                    .thenBy { it.wireSizeMm == null }
                    .thenBy { it.wireSizeMm ?: 0.0 }
                    .thenBy { it.productName.lowercase() }
            )

        /** Groups rows by metal type (ascending); product name alphabetical within a group. */
        fun group(source: List<WireDatasheet>): List<WireGroup> =
            source.groupBy { it.metalType }
                .toList()
                .sortedBy { (type, _) -> type.ordinal }
                .map { (type, rows) -> WireGroup(type, rows.sortedBy { it.productName.lowercase() }) }
    }
}
