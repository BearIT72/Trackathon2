package com.example.templates

import com.example.models.RouteCountDTO
import com.example.models.RouteDTO
import kotlinx.html.*

/**
 * Content for the Route tab
 * @param routeCounts List of RouteCountDTO objects
 * @param totalRoutes Total number of routes
 */
fun FlowContent.routePageContent(
    routeCounts: List<RouteCountDTO> = emptyList(),
    totalRoutes: Long = 0
) {
    h1 { +"Route Generation" }
    p { +"Generate routes using filtered POIs as waypoints." }

    // Mini dashboard
    div("card mt-4 mb-4") {
        div("card-body") {
            h5("card-title") { +"Route Dashboard" }
            div("row") {
                div("col-md-6") {
                    div("card bg-light") {
                        div("card-body text-center") {
                            h3 { +"$totalRoutes" }
                            p { +"Total Routes in Database" }
                        }
                    }
                }
            }
        }
    }

    // Route generation card
    div("card mt-4") {
        div("card-body") {
            h5("card-title") { +"Generate Routes" }
            p("card-text") { 
                +"Generate routes using the first point of the original track, the filtered POIs, and the last point of the original track. "
                +"The routes will be generated using the GraphHopper API with the 'hike' profile."
            }

            // Progress bar (hidden by default)
            div("mt-3 mb-3 d-none") {
                attributes["id"] = "route-progress-container"
                p { +"Generating routes... " }
                div("progress") {
                    div("progress-bar progress-bar-striped progress-bar-animated") {
                        attributes["id"] = "route-progress-bar"
                        attributes["role"] = "progressbar"
                        attributes["aria-valuenow"] = "0"
                        attributes["aria-valuemin"] = "0"
                        attributes["aria-valuemax"] = "100"
                        attributes["style"] = "width: 0%"
                    }
                }
                p {
                    attributes["id"] = "route-progress-text"
                    +"Processed 0 of 0 tracks"
                }
            }

            div("d-flex gap-2") {
                form(action = "/routes/generate", method = FormMethod.post) {
                    attributes["id"] = "generate-route-form"
                    button(type = ButtonType.submit, classes = "btn btn-primary") {
                        +"Generate Routes"
                    }
                }

                form(action = "/routes/purge", method = FormMethod.post) {
                    button(type = ButtonType.submit, classes = "btn btn-danger") {
                        +"Purge Route Data"
                    }
                }
            }
        }
    }

    // Route table
    if (routeCounts.isNotEmpty()) {
        div("card mt-4") {
            div("card-body") {
                h5("card-title") { +"Routes by Track" }

                // Add sorting script
                unsafe {
                    +"""
                    <script>
                        function sortRouteTable(n) {
                            var table, rows, switching, i, x, y, shouldSwitch, dir, switchcount = 0;
                            table = document.getElementById("routeCountsTable");
                            switching = true;
                            // Set the sorting direction to ascending:
                            dir = "asc";
                            /* Make a loop that will continue until
                            no switching has been done: */
                            while (switching) {
                                // Start by saying: no switching is done:
                                switching = false;
                                rows = table.rows;
                                /* Loop through all table rows (except the
                                first, which contains table headers): */
                                for (i = 1; i < (rows.length - 1); i++) {
                                    // Start by saying there should be no switching:
                                    shouldSwitch = false;
                                    /* Get the two elements you want to compare,
                                    one from current row and one from the next: */
                                    x = rows[i].getElementsByTagName("TD")[n];
                                    y = rows[i + 1].getElementsByTagName("TD")[n];
                                    /* Check if the two rows should switch place,
                                    based on the direction, asc or desc: */
                                    if (dir == "asc") {
                                        if (n === 1) {
                                            // For Route Count column, compare as numbers
                                            if (Number(x.innerHTML) > Number(y.innerHTML)) {
                                                // If so, mark as a switch and break the loop:
                                                shouldSwitch = true;
                                                break;
                                            }
                                        } else {
                                            if (x.innerHTML.toLowerCase() > y.innerHTML.toLowerCase()) {
                                                // If so, mark as a switch and break the loop:
                                                shouldSwitch = true;
                                                break;
                                            }
                                        }
                                    } else if (dir == "desc") {
                                        if (n === 1) {
                                            // For Route Count column, compare as numbers
                                            if (Number(x.innerHTML) < Number(y.innerHTML)) {
                                                // If so, mark as a switch and break the loop:
                                                shouldSwitch = true;
                                                break;
                                            }
                                        } else {
                                            if (x.innerHTML.toLowerCase() < y.innerHTML.toLowerCase()) {
                                                // If so, mark as a switch and break the loop:
                                                shouldSwitch = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (shouldSwitch) {
                                    /* If a switch has been marked, make the switch
                                    and mark that a switch has been done: */
                                    rows[i].parentNode.insertBefore(rows[i + 1], rows[i]);
                                    switching = true;
                                    // Each time a switch is done, increase this count by 1:
                                    switchcount++;
                                } else {
                                    /* If no switching has been done AND the direction is "asc",
                                    set the direction to "desc" and run the while loop again. */
                                    if (switchcount == 0 && dir == "asc") {
                                        dir = "desc";
                                        switching = true;
                                    }
                                }
                            }
                        }
                    </script>
                    """
                }

                table("table table-striped") {
                    id = "routeCountsTable"
                    thead {
                        tr {
                            th { 
                                attributes["onclick"] = "sortRouteTable(0)"
                                attributes["style"] = "cursor: pointer;"
                                +"Hike ID ↕" 
                            }
                            th { 
                                attributes["onclick"] = "sortRouteTable(1)"
                                attributes["style"] = "cursor: pointer;"
                                +"Route Count ↕" 
                            }
                            th { +"Actions" }
                        }
                    }
                    tbody {
                        routeCounts.forEach { routeCount ->
                            tr {
                                td { +routeCount.hikeId }
                                td { +"${routeCount.count}" }
                                td {
                                    div("d-flex gap-2") {
                                        // Refresh button (generate route for this track)
                                        form(action = "/routes/generate/${routeCount.hikeId}", method = FormMethod.post) {
                                            button(type = ButtonType.submit, classes = "btn btn-primary btn-sm") {
                                                +"Refresh"
                                            }
                                        }

                                        // Purge button (delete route for this track)
                                        form(action = "/routes/purge/${routeCount.hikeId}", method = FormMethod.post) {
                                            button(type = ButtonType.submit, classes = "btn btn-danger btn-sm") {
                                                +"Purge"
                                            }
                                        }

                                        // View button (view route for this track)
                                        a(href = "/routes/view?hikeId=${routeCount.hikeId}", classes = "btn btn-info btn-sm") {
                                            +"View"
                                        }
                                    }
                                }
                            }
                        }
                    }
                    tfoot {
                        tr {
                            td(classes = "text-end fw-bold") {
                                attributes["colspan"] = "3"
                                +"Total rows: ${routeCounts.size}"
                            }
                        }
                    }
                }
            }
        }
    } else {
        div("alert alert-info mt-4") {
            +"No route data available. Use the 'Generate Routes' button to create routes for tracks with filtered POIs."
        }
    }
}

/**
 * Content for the View Route page
 * @param hikeIds List of all hike IDs
 * @param selectedHikeId Currently selected hike ID
 * @param route Route data for the selected hike
 * @param trackData Original track GeoJSON data
 */
fun FlowContent.viewRoutePageContent(
    hikeIds: List<String> = emptyList(),
    selectedHikeId: String? = null,
    route: RouteDTO? = null,
    trackData: String? = null
) {
    h1 { +"View Route" }
    p { +"Select a hike to view its generated route on the map." }

    // Hike selection form
    div("card mt-4") {
        div("card-body") {
            h5("card-title") { +"Select Hike" }

            form(action = "/routes/view", method = FormMethod.get) {
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

    // Map and route display
    if (selectedHikeId != null && trackData != null && route != null) {
        div("card mt-4") {
            div("card-body") {
                h5("card-title") { +"Map for Hike: $selectedHikeId" }

                // Route information
                div("mb-3") {
                    p { 
                        +"Distance: ${String.format("%.2f", route.distance?.div(1000) ?: 0.0)} km" 
                        if (route.duration != null) {
                            +" | Duration: ${formatDuration(route.duration)}"
                        }
                    }
                }

                // Map container
                div {
                    id = "map"
                    style = "height: 500px; width: 100%;"
                }

                // Back button
                div("mt-3") {
                    a(href = "/routes", classes = "btn btn-secondary") {
                        +"Back to Routes"
                    }
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

                // JavaScript to initialize the map and add track and route
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

                            // Add original track to map as a blue line
                            const trackLayer = L.geoJSON(trackData, {
                                style: {
                                    color: 'blue',
                                    weight: 3,
                                    opacity: 0.7
                                }
                            }).addTo(map);

                            // Parse route GeoJSON
                            const routeData = ${route.routeJson};

                            // Convert GraphHopper response to GeoJSON format
                            let routeGeoJson = null;
                            let startPoint = null;
                            let endPoint = null;

                            if (routeData.paths && routeData.paths.length > 0) {
                                const path = routeData.paths[0];

                                // Check if points.coordinates exists (GeoJSON format)
                                if (path.points && path.points.coordinates && path.points.coordinates.length > 0) {
                                    // Already in GeoJSON format
                                    const points = path.points.coordinates;
                                    startPoint = points[0];
                                    endPoint = points[points.length - 1];

                                    routeGeoJson = {
                                        "type": "Feature",
                                        "geometry": {
                                            "type": "LineString",
                                            "coordinates": points
                                        },
                                        "properties": {}
                                    };
                                } 
                                // Check if points exists as encoded polyline
                                else if (path.points) {
                                    console.log("Route data has encoded polyline format");
                                    // Try to use the points_encoded format
                                    if (path.snapped_waypoints && path.snapped_waypoints.coordinates) {
                                        const waypoints = path.snapped_waypoints.coordinates;
                                        if (waypoints.length >= 2) {
                                            startPoint = waypoints[0];
                                            endPoint = waypoints[waypoints.length - 1];
                                        }
                                    }

                                    // Create a simple GeoJSON with just the waypoints
                                    routeGeoJson = {
                                        "type": "Feature",
                                        "geometry": {
                                            "type": "LineString",
                                            "coordinates": path.snapped_waypoints ? path.snapped_waypoints.coordinates : []
                                        },
                                        "properties": {}
                                    };
                                }
                            }

                            // Add route to map as a green line if we have valid GeoJSON
                            let routeLayer = null;
                            if (routeGeoJson) {
                                routeLayer = L.geoJSON(routeGeoJson, {
                                    style: {
                                        color: 'green',
                                        weight: 4,
                                        opacity: 0.8
                                    }
                                }).addTo(map);
                            } else {
                                console.error("Could not parse route data:", routeData);
                            }

                            // Add start and end markers
                            if (startPoint) {
                                const startIcon = L.divIcon({
                                    html: '<div style="background-color: green; width: 12px; height: 12px; border-radius: 50%; border: 2px solid white;"></div>',
                                    className: 'start-marker',
                                    iconSize: [16, 16],
                                    iconAnchor: [8, 8]
                                });
                                L.marker([startPoint[1], startPoint[0]], { icon: startIcon }).addTo(map)
                                    .bindPopup('Start Point');
                            }

                            if (endPoint) {
                                const endIcon = L.divIcon({
                                    html: '<div style="background-color: red; width: 12px; height: 12px; border-radius: 50%; border: 2px solid white;"></div>',
                                    className: 'end-marker',
                                    iconSize: [16, 16],
                                    iconAnchor: [8, 8]
                                });
                                L.marker([endPoint[1], endPoint[0]], { icon: endIcon }).addTo(map)
                                    .bindPopup('End Point');
                            }

                            // Fit map to bounds of both layers
                            let bounds = trackLayer.getBounds();
                            if (routeLayer) {
                                bounds.extend(routeLayer.getBounds());
                            }
                            map.fitBounds(bounds);
                        });
                        """
                    }
                }
            }
        }
    } else if (selectedHikeId != null) {
        div("alert alert-warning mt-4") {
            +"No route data available for the selected hike. Please make sure you have generated a route for this track."
        }
    }
}

/**
 * Format duration in seconds to a human-readable string
 * @param seconds Duration in seconds
 * @return Formatted duration string (e.g., "2h 30m")
 */
private fun formatDuration(seconds: Double): String {
    val hours = (seconds / 3600).toInt()
    val minutes = ((seconds % 3600) / 60).toInt()

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
