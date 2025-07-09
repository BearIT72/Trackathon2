package com.example.templates

import com.example.models.POICountDTO
import com.example.models.POIDTO
import com.example.models.FilteredPOICountDTO
import com.example.models.FilteredPOIDTO
import io.ktor.server.html.*
import kotlinx.html.*

class IndexPage : Template<HTML> {
    val content = Placeholder<FlowContent>()
    var activeTab = "home"

    override fun HTML.apply() {
        head {
            title("Ktor Web App")
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css")
        }
        body {
            div("container mt-5") {
                // Navigation tabs
                ul("nav nav-tabs mb-4") {
                    li("nav-item") {
                        a(classes = "nav-link ${if (activeTab == "home") "active" else ""}", href = "/") {
                            +"Home"
                        }
                    }
                    li("nav-item") {
                        a(classes = "nav-link ${if (activeTab == "import") "active" else ""}", href = "/import") {
                            +"Import Data"
                        }
                    }
                    li("nav-item") {
                        a(classes = "nav-link ${if (activeTab == "pois") "active" else ""}", href = "/pois") {
                            +"POIs"
                        }
                    }
                    li("nav-item") {
                        a(classes = "nav-link ${if (activeTab == "view-pois") "active" else ""}", href = "/view-pois") {
                            +"View POIs"
                        }
                    }
                    li("nav-item") {
                        a(classes = "nav-link ${if (activeTab == "filter-pois") "active" else ""}", href = "/filter-pois") {
                            +"Filter POIs"
                        }
                    }
                    li("nav-item") {
                        a(classes = "nav-link ${if (activeTab == "routes") "active" else ""}", href = "/routes") {
                            +"Route"
                        }
                    }
                    li("nav-item") {
                        a(classes = "nav-link ${if (activeTab == "api") "active" else ""}", href = "/api/health") {
                            +"API Health"
                        }
                    }
                }

                // Content area
                div("tab-content") {
                    insert(content)
                }
            }
            script(src = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js") {}

            // Add JavaScript for POI search progress bar
            if (activeTab == "pois") {
                script(type = "text/javascript") {
                    unsafe {
                        +"""
                        document.addEventListener('DOMContentLoaded', function() {
                            const searchForm = document.getElementById('search-poi-form');
                            const progressContainer = document.getElementById('poi-progress-container');
                            const progressBar = document.getElementById('poi-progress-bar');
                            const progressText = document.getElementById('poi-progress-text');

                            if (searchForm) {
                                searchForm.addEventListener('submit', function(e) {
                                    // Show progress bar when form is submitted
                                    progressContainer.classList.remove('d-none');

                                    // Start polling for progress
                                    checkProgress();
                                });
                            }

                            function checkProgress() {
                                fetch('/api/poi/progress')
                                    .then(response => response.json())
                                    .then(data => {
                                        if (data.inProgress) {
                                            // Update progress bar
                                            const percent = data.total > 0 ? Math.round((data.current / data.total) * 100) : 0;
                                            progressBar.style.width = percent + '%';
                                            progressBar.setAttribute('aria-valuenow', percent);
                                            progressText.textContent = 'Processed ' + data.current + ' of ' + data.total + ' tracks';

                                            // Continue polling
                                            setTimeout(checkProgress, 500);
                                        } else if (data.total > 0 && data.current >= data.total) {
                                            // Search completed
                                            progressBar.style.width = '100%';
                                            progressBar.setAttribute('aria-valuenow', 100);
                                            progressText.textContent = 'Completed! Processed ' + data.current + ' of ' + data.total + ' tracks';
                                        }
                                    })
                                    .catch(error => {
                                        console.error('Error checking progress:', error);
                                    });
                            }

                            // Check if a search is already in progress when page loads
                            checkProgress();
                        });
                        """
                    }
                }
            }

            // Add JavaScript for POI filter progress bar
            if (activeTab == "filter-pois") {
                script(type = "text/javascript") {
                    unsafe {
                        +"""
                        document.addEventListener('DOMContentLoaded', function() {
                            const filterForm = document.getElementById('filter-poi-form');
                            const progressContainer = document.getElementById('filter-progress-container');
                            const progressBar = document.getElementById('filter-progress-bar');
                            const progressText = document.getElementById('filter-progress-text');

                            if (filterForm) {
                                filterForm.addEventListener('submit', function(e) {
                                    // Show progress bar when form is submitted
                                    progressContainer.classList.remove('d-none');

                                    // Start polling for progress
                                    checkProgress();
                                });
                            }

                            function checkProgress() {
                                fetch('/api/filter/progress')
                                    .then(response => response.json())
                                    .then(data => {
                                        if (data.inProgress) {
                                            // Update progress bar
                                            const percent = data.total > 0 ? Math.round((data.current / data.total) * 100) : 0;
                                            progressBar.style.width = percent + '%';
                                            progressBar.setAttribute('aria-valuenow', percent);
                                            progressText.textContent = 'Processed ' + data.current + ' of ' + data.total + ' tracks';

                                            // Continue polling
                                            setTimeout(checkProgress, 500);
                                        } else if (data.total > 0 && data.current >= data.total) {
                                            // Filtering completed
                                            progressBar.style.width = '100%';
                                            progressBar.setAttribute('aria-valuenow', 100);
                                            progressText.textContent = 'Completed! Processed ' + data.current + ' of ' + data.total + ' tracks';
                                        }
                                    })
                                    .catch(error => {
                                        console.error('Error checking progress:', error);
                                    });
                            }

                            // Check if filtering is already in progress when page loads
                            checkProgress();
                        });
                        """
                    }
                }
            }

            // Add JavaScript for route generation progress bar
            if (activeTab == "routes") {
                script(type = "text/javascript") {
                    unsafe {
                        +"""
                        document.addEventListener('DOMContentLoaded', function() {
                            const generateForm = document.getElementById('generate-route-form');
                            const progressContainer = document.getElementById('route-progress-container');
                            const progressBar = document.getElementById('route-progress-bar');
                            const progressText = document.getElementById('route-progress-text');

                            if (generateForm) {
                                generateForm.addEventListener('submit', function(e) {
                                    // Show progress bar when form is submitted
                                    progressContainer.classList.remove('d-none');

                                    // Start polling for progress
                                    checkProgress();
                                });
                            }

                            function checkProgress() {
                                fetch('/api/route/progress')
                                    .then(response => response.json())
                                    .then(data => {
                                        if (data.inProgress) {
                                            // Update progress bar
                                            const percent = data.total > 0 ? Math.round((data.current / data.total) * 100) : 0;
                                            progressBar.style.width = percent + '%';
                                            progressBar.setAttribute('aria-valuenow', percent);
                                            progressText.textContent = 'Processed ' + data.current + ' of ' + data.total + ' tracks';

                                            // Continue polling
                                            setTimeout(checkProgress, 500);
                                        } else if (data.total > 0 && data.current >= data.total) {
                                            // Route generation completed
                                            progressBar.style.width = '100%';
                                            progressBar.setAttribute('aria-valuenow', 100);
                                            progressText.textContent = 'Completed! Processed ' + data.current + ' of ' + data.total + ' tracks';
                                        }
                                    })
                                    .catch(error => {
                                        console.error('Error checking progress:', error);
                                    });
                            }

                            // Check if route generation is already in progress when page loads
                            checkProgress();
                        });
                        """
                    }
                }
            }
        }
    }
}

fun FlowContent.welcomeContent() {
    h1 { +"Welcome to Ktor Web App" }
    p { +"This is a simple Ktor web application with up-to-date dependencies." }
    div("mt-4") {
        h2 { +"Features" }
        ul {
            li { +"Kotlin 1.9.22" }
            li { +"Ktor 2.3.7" }
            li { +"HTML DSL for templates" }
            li { +"Content negotiation with JSON" }
            li { +"Bootstrap 5.3.2 for styling" }
        }
    }
}

fun FlowContent.importPageContent(dataCount: Long = 0) {
    h1 { +"Data Import" }
    p { +"Import GeoJSON data from CSV file." }

    // Mini dashboard
    div("card mt-4 mb-4") {
        div("card-body") {
            h5("card-title") { +"Data Dashboard" }
            div("row") {
                div("col-md-6") {
                    div("card bg-light") {
                        div("card-body text-center") {
                            h3 { +"$dataCount" }
                            p { +"Records in Database" }
                        }
                    }
                }
            }
        }
    }

    div("card mt-4") {
        div("card-body") {
            h5("card-title") { +"Import Data" }
            p("card-text") { 
                +"Click the button below to import data from "
                code { +"input/flat/id_geojson.csv" }
                +". The data will be stored in the database."
            }

            div("d-flex gap-2") {
                form(action = "/import", method = FormMethod.post) {
                    button(type = ButtonType.submit, classes = "btn btn-primary") {
                        +"Start Import"
                    }
                }

                form(action = "/purge", method = FormMethod.post) {
                    button(type = ButtonType.submit, classes = "btn btn-danger") {
                        +"Purge All Data"
                    }
                }
            }
        }
    }
}

fun FlowContent.poisPageContent(poiCounts: List<POICountDTO> = emptyList(), totalPois: Long = 0) {
    h1 { +"Points of Interest (POIs)" }
    p { +"Search for POIs near your tracks using Overpass API." }

    // Mini dashboard
    div("card mt-4 mb-4") {
        div("card-body") {
            h5("card-title") { +"POI Dashboard" }
            div("row") {
                div("col-md-6") {
                    div("card bg-light") {
                        div("card-body text-center") {
                            h3 { +"$totalPois" }
                            p { +"Total POIs in Database" }
                        }
                    }
                }
            }
        }
    }

    // Search POIs card
    div("card mt-4") {
        div("card-body") {
            h5("card-title") { +"Search POIs" }
            p("card-text") { 
                +"Click the button below to search for Points of Interest near your tracks using Overpass API. "
                +"The search will use the bounding box of each track to find nearby POIs."
            }

            // Progress bar (hidden by default)
            div("mt-3 mb-3 d-none") {
                attributes["id"] = "poi-progress-container"
                p { +"Searching for POIs... " }
                div("progress") {
                    div("progress-bar progress-bar-striped progress-bar-animated") {
                        attributes["id"] = "poi-progress-bar"
                        attributes["role"] = "progressbar"
                        attributes["aria-valuenow"] = "0"
                        attributes["aria-valuemin"] = "0"
                        attributes["aria-valuemax"] = "100"
                        attributes["style"] = "width: 0%"
                    }
                }
                p {
                    attributes["id"] = "poi-progress-text"
                    +"Processed 0 of 0 tracks"
                }
            }

            div("d-flex gap-2") {
                form(action = "/pois/search", method = FormMethod.post) {
                    attributes["id"] = "search-poi-form"
                    button(type = ButtonType.submit, classes = "btn btn-primary") {
                        +"Search POIs"
                    }
                }

                form(action = "/pois/purge", method = FormMethod.post) {
                    button(type = ButtonType.submit, classes = "btn btn-danger") {
                        +"Purge POI Data"
                    }
                }
            }
        }
    }

    // POI counts table
    if (poiCounts.isNotEmpty()) {
        div("card mt-4") {
            div("card-body") {
                h5("card-title") { +"POI Counts by Track" }
                table("table table-striped") {
                    thead {
                        tr {
                            th { +"Hike ID" }
                            th { +"POI Count" }
                            th { +"Actions" }
                        }
                    }
                    tbody {
                        poiCounts.forEach { poiCount ->
                            tr {
                                td { +poiCount.hikeId }
                                td { +"${poiCount.count}" }
                                td {
                                    div("d-flex gap-2") {
                                        // Refresh button (search POIs for this track)
                                        form(action = "/pois/search/${poiCount.hikeId}", method = FormMethod.post) {
                                            button(type = ButtonType.submit, classes = "btn btn-primary btn-sm") {
                                                +"Refresh"
                                            }
                                        }

                                        // Purge button (delete POIs for this track)
                                        form(action = "/pois/purge/${poiCount.hikeId}", method = FormMethod.post) {
                                            button(type = ButtonType.submit, classes = "btn btn-danger btn-sm") {
                                                +"Purge"
                                            }
                                        }

                                        // View button (view POIs for this track)
                                        a(href = "/view-pois?hikeId=${poiCount.hikeId}", classes = "btn btn-info btn-sm") {
                                            +"View"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Add button to search POIs for all tracks missing them
                if (poiCounts.isNotEmpty()) {
                    div("mt-3") {
                        form(action = "/pois/search-missing", method = FormMethod.post) {
                            button(type = ButtonType.submit, classes = "btn btn-success") {
                                +"Search POIs for All Tracks Missing Them"
                            }
                        }
                    }
                }
            }
        }
    } else {
        div("alert alert-info mt-4") {
            +"No POI data available. Use the 'Search POIs' button to find Points of Interest near your tracks."
        }
    }
}

/**
 * Content for the Filter POIs tab
 * @param filteredPoiCounts List of FilteredPOICountDTO objects
 * @param totalFilteredPois Total number of filtered POIs
 * @param totalArtificialPois Total number of artificial POIs
 * @param maxPOIs Maximum number of POIs to keep per track
 */
fun FlowContent.filterPoisPageContent(
    filteredPoiCounts: List<FilteredPOICountDTO> = emptyList(),
    totalFilteredPois: Long = 0,
    totalArtificialPois: Long = 0,
    maxPOIs: Int = 10
) {
    h1 { +"Filter Points of Interest (POIs)" }
    p { +"Filter POIs to keep only those close to the original track and distribute them evenly." }

    // Mini dashboard
    div("card mt-4 mb-4") {
        div("card-body") {
            h5("card-title") { +"Filtered POI Dashboard" }
            div("row") {
                div("col-md-4") {
                    div("card bg-light") {
                        div("card-body text-center") {
                            h3 { +"$totalFilteredPois" }
                            p { +"Total Filtered POIs" }
                        }
                    }
                }
                div("col-md-4") {
                    div("card bg-light") {
                        div("card-body text-center") {
                            h3 { +"$totalArtificialPois" }
                            p { +"Artificial POIs" }
                        }
                    }
                }
                div("col-md-4") {
                    div("card bg-light") {
                        div("card-body text-center") {
                            h3 { +"${totalFilteredPois - totalArtificialPois}" }
                            p { +"Real POIs" }
                        }
                    }
                }
            }
        }
    }

    // Filter POIs card
    div("card mt-4") {
        div("card-body") {
            h5("card-title") { +"Filter POIs" }
            p("card-text") { 
                +"Filter POIs to keep only those within 500m of the original track. "
                +"If there are more POIs than the maximum, they will be distributed evenly along the track. "
                +"If there are fewer POIs than the maximum, artificial POIs will be created on the track."
            }

            // Progress bar (hidden by default)
            div("mt-3 mb-3 d-none") {
                attributes["id"] = "filter-progress-container"
                p { +"Filtering POIs... " }
                div("progress") {
                    div("progress-bar progress-bar-striped progress-bar-animated") {
                        attributes["id"] = "filter-progress-bar"
                        attributes["role"] = "progressbar"
                        attributes["aria-valuenow"] = "0"
                        attributes["aria-valuemin"] = "0"
                        attributes["aria-valuemax"] = "100"
                        attributes["style"] = "width: 0%"
                    }
                }
                p {
                    attributes["id"] = "filter-progress-text"
                    +"Processed 0 of 0 tracks"
                }
            }

            form(action = "/filter-pois/filter", method = FormMethod.post) {
                attributes["id"] = "filter-poi-form"

                // Max POIs input
                div("mb-3") {
                    label("form-label") {
                        htmlFor = "maxPOIs"
                        +"Maximum POIs per track"
                    }
                    input(type = InputType.number, classes = "form-control") {
                        id = "maxPOIs"
                        name = "maxPOIs"
                        value = maxPOIs.toString()
                        min = "1"
                        max = "100"
                    }
                    div("form-text") {
                        +"Enter the maximum number of POIs to keep per track (1-100)."
                    }
                }

                div("d-flex gap-2") {
                    button(type = ButtonType.submit, classes = "btn btn-primary") {
                        +"Filter POIs"
                    }

                    a(href = "/filter-pois/purge", classes = "btn btn-danger") {
                        attributes["onclick"] = "return confirm('Are you sure you want to purge all filtered POI data?');"
                        +"Purge Filtered POI Data"
                    }
                }
            }
        }
    }

    // Filtered POI counts table
    if (filteredPoiCounts.isNotEmpty()) {
        div("card mt-4") {
            div("card-body") {
                h5("card-title") { +"Filtered POI Counts by Track" }
                table("table table-striped") {
                    thead {
                        tr {
                            th { +"Hike ID" }
                            th { +"Total POIs" }
                            th { +"Artificial POIs" }
                            th { +"Actions" }
                        }
                    }
                    tbody {
                        filteredPoiCounts.forEach { poiCount ->
                            tr {
                                td { +poiCount.hikeId }
                                td { +"${poiCount.count}" }
                                td { +"${poiCount.artificialCount}" }
                                td {
                                    div("d-flex gap-2") {
                                        // Refresh button (filter POIs for this track)
                                        form(action = "/filter-pois/filter/${poiCount.hikeId}", method = FormMethod.post) {
                                            input(type = InputType.hidden, name = "maxPOIs") {
                                                value = maxPOIs.toString()
                                            }
                                            button(type = ButtonType.submit, classes = "btn btn-primary btn-sm") {
                                                +"Refresh"
                                            }
                                        }

                                        // Purge button (delete filtered POIs for this track)
                                        form(action = "/filter-pois/purge/${poiCount.hikeId}", method = FormMethod.post) {
                                            button(type = ButtonType.submit, classes = "btn btn-danger btn-sm") {
                                                +"Purge"
                                            }
                                        }

                                        // View button (view filtered POIs for this track)
                                        a(href = "/filter-pois/view?hikeId=${poiCount.hikeId}", classes = "btn btn-info btn-sm") {
                                            +"View"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        div("alert alert-info mt-4") {
            +"No filtered POI data available. Use the 'Filter POIs' button to filter Points of Interest near your tracks."
        }
    }
}

/**
 * Content for the View Filtered POIs tab
 * @param hikeIds List of all hike IDs
 * @param selectedHikeId Currently selected hike ID
 * @param pois List of filtered POIs for the selected hike
 * @param trackData GeoJSON data for the selected hike
 */
fun FlowContent.viewFilteredPoisPageContent(
    hikeIds: List<String> = emptyList(),
    selectedHikeId: String? = null,
    pois: List<FilteredPOIDTO> = emptyList(),
    trackData: String? = null
) {
    h1 { +"View Filtered POIs" }
    p { +"Select a hike to view its filtered POIs on the map." }

    // Hike selection form
    div("card mt-4") {
        div("card-body") {
            h5("card-title") { +"Select Hike" }

            form(action = "/filter-pois/view", method = FormMethod.get) {
                div("mb-3") {
                    label("form-label") {
                        htmlFor = "hikeId"
                        +"Hike ID"
                    }
                    select("form-select") {
                        id = "hikeId"
                        name = "hikeId"
                        attributes["onChange"] = "this.form.submit()"

                        option {
                            value = ""
                            +"-- Select a hike --"
                        }

                        hikeIds.forEach { hikeId ->
                            option {
                                value = hikeId
                                selected = hikeId == selectedHikeId
                                +hikeId
                            }
                        }
                    }
                }
            }
        }
    }

    // Map and POI display
    if (selectedHikeId != null && trackData != null) {
        div("card mt-4") {
            div("card-body") {
                h5("card-title") { +"Map for Hike: $selectedHikeId" }

                // Count real and artificial POIs
                val realPois = pois.count { !it.isArtificial }
                val artificialPois = pois.count { it.isArtificial }

                p { +"Filtered POIs found: ${pois.size} (${realPois} real, ${artificialPois} artificial)" }

                // Map container
                div {
                    id = "map"
                    style = "height: 500px; width: 100%;"
                }

                // Include Leaflet CSS and JS
                unsafe {
                    +"""
                    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" 
                        integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" 
                        crossorigin=""/>
                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" 
                        integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" 
                        crossorigin=""></script>
                    """
                }

                // JavaScript to initialize the map and add POIs and track
                script {
                    unsafe {
                        +"""
                        document.addEventListener('DOMContentLoaded', function() {
                            // Initialize map
                            const map = L.map('map');

                            // Add OpenStreetMap tile layer
                            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                            }).addTo(map);

                            // Parse track GeoJSON
                            const trackData = ${trackData};

                            // Add track to map as a blue line
                            const trackLayer = L.geoJSON(trackData, {
                                style: {
                                    color: 'blue',
                                    weight: 3,
                                    opacity: 0.7
                                }
                            }).addTo(map);

                            // Fit map to track bounds
                            map.fitBounds(trackLayer.getBounds());

                            // Add POI markers
                            const pois = [
                                ${pois.joinToString(",\n                                ") { poi -> 
                                    """{
                                    id: "${poi.id}",
                                    name: "${poi.name ?: "Unknown"}",
                                    type: "${poi.type}",
                                    lat: ${poi.latitude},
                                    lng: ${poi.longitude},
                                    isArtificial: ${poi.isArtificial}
                                }"""
                                }}
                            ];

                            pois.forEach(function(poi) {
                                // Use different marker colors for real vs artificial POIs
                                const markerColor = poi.isArtificial ? 'red' : 'green';
                                const markerIcon = L.divIcon({
                                    html: '<div style="background-color: ' + markerColor + '; width: 12px; height: 12px; border-radius: 50%; border: 2px solid white;"></div>',
                                    className: 'custom-marker',
                                    iconSize: [16, 16],
                                    iconAnchor: [8, 8]
                                });

                                const marker = L.marker([poi.lat, poi.lng], { icon: markerIcon }).addTo(map);
                                marker.bindPopup('<b>' + poi.name + '</b><br>Type: ' + poi.type + 
                                    (poi.isArtificial ? '<br><i>Artificial POI</i>' : ''));
                            });
                        });
                        """
                    }
                }
            }
        }
    } else if (selectedHikeId != null) {
        div("alert alert-warning mt-4") {
            +"No track data available for the selected hike or no filtered POIs found. Please make sure you have imported the track data and filtered POIs."
        }
    }
}

/**
 * Content for the View POIs tab
 * @param hikeIds List of all hike IDs
 * @param selectedHikeId Currently selected hike ID
 * @param pois List of POIs for the selected hike
 * @param trackData GeoJSON data for the selected hike
 */
fun FlowContent.viewPoisPageContent(
    hikeIds: List<String> = emptyList(),
    selectedHikeId: String? = null,
    pois: List<POIDTO> = emptyList(),
    trackData: String? = null
) {
    h1 { +"View POIs" }
    p { +"Select a hike to view its POIs on the map." }

    // Hike selection form
    div("card mt-4") {
        div("card-body") {
            h5("card-title") { +"Select Hike" }

            form(action = "/view-pois", method = FormMethod.get) {
                div("mb-3") {
                    label("form-label") {
                        htmlFor = "hikeId"
                        +"Hike ID"
                    }
                    select("form-select") {
                        id = "hikeId"
                        name = "hikeId"
                        attributes["onChange"] = "this.form.submit()"

                        option {
                            value = ""
                            +"-- Select a hike --"
                        }

                        hikeIds.forEach { hikeId ->
                            option {
                                value = hikeId
                                selected = hikeId == selectedHikeId
                                +hikeId
                            }
                        }
                    }
                }
            }
        }
    }

    // Map and POI display
    if (selectedHikeId != null && trackData != null) {
        div("card mt-4") {
            div("card-body") {
                h5("card-title") { +"Map for Hike: $selectedHikeId" }
                p { +"POIs found: ${pois.size}" }

                // Map container
                div {
                    id = "map"
                    style = "height: 500px; width: 100%;"
                }

                // Include Leaflet CSS and JS
                unsafe {
                    +"""
                    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" 
                        integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" 
                        crossorigin=""/>
                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" 
                        integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" 
                        crossorigin=""></script>
                    """
                }

                /**
                 * Content for the View Filtered POIs tab
                 * @param hikeIds List of all hike IDs
                 * @param selectedHikeId Currently selected hike ID
                 * @param pois List of filtered POIs for the selected hike
                 * @param trackData GeoJSON data for the selected hike
                 */
                fun FlowContent.viewFilteredPoisPageContent(
                    hikeIds: List<String> = emptyList(),
                    selectedHikeId: String? = null,
                    pois: List<FilteredPOIDTO> = emptyList(),
                    trackData: String? = null
                ) {
                    h1 { +"View Filtered POIs" }
                    p { +"Select a hike to view its filtered POIs on the map." }

                    // Hike selection form
                    div("card mt-4") {
                        div("card-body") {
                            h5("card-title") { +"Select Hike" }

                            form(action = "/filter-pois/view", method = FormMethod.get) {
                                div("mb-3") {
                                    label("form-label") {
                                        htmlFor = "hikeId"
                                        +"Hike ID"
                                    }
                                    select("form-select") {
                                        id = "hikeId"
                                        name = "hikeId"
                                        attributes["onChange"] = "this.form.submit()"

                                        option {
                                            value = ""
                                            +"-- Select a hike --"
                                        }

                                        hikeIds.forEach { hikeId ->
                                            option {
                                                value = hikeId
                                                selected = hikeId == selectedHikeId
                                                +hikeId
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Map and POI display
                    if (selectedHikeId != null && trackData != null) {
                        div("card mt-4") {
                            div("card-body") {
                                h5("card-title") { +"Map for Hike: $selectedHikeId" }

                                // Count real and artificial POIs
                                val realPois = pois.count { !it.isArtificial }
                                val artificialPois = pois.count { it.isArtificial }

                                p { +"Filtered POIs found: ${pois.size} (${realPois} real, ${artificialPois} artificial)" }

                                // Map container
                                div {
                                    id = "map"
                                    style = "height: 500px; width: 100%;"
                                }

                                // Include Leaflet CSS and JS
                                unsafe {
                                    +"""
                                    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" 
                                        integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" 
                                        crossorigin=""/>
                                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" 
                                        integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" 
                                        crossorigin=""></script>
                                    """
                                }

                                // JavaScript to initialize the map and add POIs and track
                                script {
                                    unsafe {
                                        +"""
                                        document.addEventListener('DOMContentLoaded', function() {
                                            // Initialize map
                                            const map = L.map('map');

                                            // Add OpenStreetMap tile layer
                                            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                                            }).addTo(map);

                                            // Parse track GeoJSON
                                            const trackData = ${trackData};

                                            // Add track to map as a blue line
                                            const trackLayer = L.geoJSON(trackData, {
                                                style: {
                                                    color: 'blue',
                                                    weight: 3,
                                                    opacity: 0.7
                                                }
                                            }).addTo(map);

                                            // Fit map to track bounds
                                            map.fitBounds(trackLayer.getBounds());

                                            // Add POI markers
                                            const pois = [
                                                ${pois.joinToString(",\n                                ") { poi -> 
                                                    """{
                                                    id: "${poi.id}",
                                                    name: "${poi.name ?: "Unknown"}",
                                                    type: "${poi.type}",
                                                    lat: ${poi.latitude},
                                                    lng: ${poi.longitude},
                                                    isArtificial: ${poi.isArtificial}
                                                }"""
                                                }}
                                            ];

                                            pois.forEach(function(poi) {
                                                // Use different marker colors for real vs artificial POIs
                                                const markerColor = poi.isArtificial ? 'red' : 'green';
                                                const markerIcon = L.divIcon({
                                                    html: '<div style="background-color: ' + markerColor + '; width: 12px; height: 12px; border-radius: 50%; border: 2px solid white;"></div>',
                                                    className: 'custom-marker',
                                                    iconSize: [16, 16],
                                                    iconAnchor: [8, 8]
                                                });

                                                const marker = L.marker([poi.lat, poi.lng], { icon: markerIcon }).addTo(map);
                                                marker.bindPopup('<b>' + poi.name + '</b><br>Type: ' + poi.type + 
                                                    (poi.isArtificial ? '<br><i>Artificial POI</i>' : ''));
                                            });
                                        });
                                        """
                                    }
                                }
                            }
                        }
                    } else if (selectedHikeId != null) {
                        div("alert alert-warning mt-4") {
                            +"No track data available for the selected hike or no filtered POIs found. Please make sure you have imported the track data and filtered POIs."
                        }
                    }
                }

                // JavaScript to initialize the map and add POIs and track
                script {
                    unsafe {
                        +"""
                        document.addEventListener('DOMContentLoaded', function() {
                            // Initialize map
                            const map = L.map('map');

                            // Add OpenStreetMap tile layer
                            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                            }).addTo(map);

                            // Parse track GeoJSON
                            const trackData = ${trackData};

                            // Add track to map as a blue line
                            const trackLayer = L.geoJSON(trackData, {
                                style: {
                                    color: 'blue',
                                    weight: 3,
                                    opacity: 0.7
                                }
                            }).addTo(map);

                            // Fit map to track bounds
                            map.fitBounds(trackLayer.getBounds());

                            // Add POI markers
                            const pois = [
                                ${pois.joinToString(",\n                                ") { poi -> 
                                    """{
                                    id: "${poi.id}",
                                    name: "${poi.name ?: "Unknown"}",
                                    type: "${poi.type}",
                                    lat: ${poi.latitude},
                                    lng: ${poi.longitude}
                                }"""
                                }}
                            ];

                            pois.forEach(function(poi) {
                                const marker = L.marker([poi.lat, poi.lng]).addTo(map);
                                marker.bindPopup('<b>' + poi.name + '</b><br>Type: ' + poi.type);
                            });
                        });
                        """
                    }
                }
            }
        }
    } else if (selectedHikeId != null) {
        div("alert alert-warning mt-4") {
            +"No track data available for the selected hike or no POIs found. Please make sure you have imported the track data and searched for POIs."
        }
    }
}
