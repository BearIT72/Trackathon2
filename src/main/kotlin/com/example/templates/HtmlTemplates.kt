package com.example.templates

import com.example.models.POICountDTO
import com.example.models.POIDTO
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
