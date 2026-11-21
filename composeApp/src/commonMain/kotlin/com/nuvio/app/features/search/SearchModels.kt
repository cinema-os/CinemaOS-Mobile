package com.nuvio.app.features.search

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.HomeCatalogSection

enum class SearchEmptyStateReason {
    NoActiveAddons,
    NoSearchCatalogs,
    NoResults,
    RequestFailed,
}

data class SearchUiState(
    val isLoading: Boolean = false,
    val sections: List<HomeCatalogSection> = emptyList(),
    val emptyStateReason: SearchEmptyStateReason? = null,
    val errorMessage: String? = null,
)

enum class DiscoverEmptyStateReason {
    NoActiveAddons,
    NoDiscoverCatalogs,
    NoResults,
    RequestFailed,
}

data class DiscoverCatalogOption(
    val key: String,
    val addonName: String,
    val manifestUrl: String,
    val type: String,
    val catalogId: String,
    val catalogName: String,
    val genreOptions: List<String> = emptyList(),
    val genreRequired: Boolean = false,
    val supportsPagination: Boolean = false,
    val server: CatalogTarget.Server? = null,
    /** True for the built-in TMDB discover source that is driven by [DiscoverFilters] instead of addon extras. */
    val isTmdb: Boolean = false,
)

data class DiscoverUiState(
    val typeOptions: List<String> = emptyList(),
    val selectedType: String? = null,
    val catalogOptions: List<DiscoverCatalogOption> = emptyList(),
    val selectedCatalogKey: String? = null,
    val selectedGenre: String? = null,
    val filters: DiscoverFilters = DiscoverFilters(),
    val filterOptions: DiscoverFilterOptions = DiscoverFilterOptions(),
    val items: List<MetaPreview> = emptyList(),
    val isLoading: Boolean = false,
    val nextSkip: Int? = null,
    val consecutiveDuplicatePages: Int = 0,
    val emptyStateReason: DiscoverEmptyStateReason? = null,
    val errorMessage: String? = null,
) {
    val selectedCatalog: DiscoverCatalogOption?
        get() = catalogOptions.firstOrNull { it.key == selectedCatalogKey }

    /**
     * Genres offered for the selected type: the union across every catalog of that type, so a genre can be picked
     * even when the currently selected catalog (e.g. "Trending") has no genre extra. Selecting one switches to a
     * catalog that supports it.
     */
    val genreOptions: List<String>
        get() = catalogOptions.filterNot { it.isTmdb }.flatMap { it.genreOptions }.distinct()

    val isTmdbSelected: Boolean
        get() = selectedCatalog?.isTmdb == true

    /** Effective TMDB media type: anime follows its Format filter (TV series / Movie), the rest follow the catalog type. */
    val isTmdbTv: Boolean
        get() = selectedCatalog?.let { it.isTmdb && discoverIsTv(it.type, filters) } == true

    val isTmdbAnime: Boolean
        get() = selectedCatalog?.let { it.isTmdb && it.type == "anime" } == true

    val canLoadMore: Boolean
        get() = nextSkip != null
}
