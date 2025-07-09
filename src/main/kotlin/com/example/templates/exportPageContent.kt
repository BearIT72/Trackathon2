package com.example.templates

import kotlinx.html.*

/**
 * Content for the Export tab
 */
fun FlowContent.exportPageContent() {
    h1 { +"Export Data" }
    p { +"Export hike data to GeoJSON files." }

    // Export card
    div("card mt-4") {
        div("card-body") {
            h5("card-title") { +"Export to GeoJSONs" }
            p("card-text") { 
                +"Click the button below to export all hikes to GeoJSON files. "
                +"Each hike will be exported to a separate file named after the hike ID in the output folder."
            }

            // Progress bar (hidden by default)
            div("mt-3 mb-3 d-none") {
                attributes["id"] = "export-progress-container"
                p { +"Exporting GeoJSON files... " }
                div("progress") {
                    div("progress-bar progress-bar-striped progress-bar-animated") {
                        attributes["id"] = "export-progress-bar"
                        attributes["role"] = "progressbar"
                        attributes["aria-valuenow"] = "0"
                        attributes["aria-valuemin"] = "0"
                        attributes["aria-valuemax"] = "100"
                        attributes["style"] = "width: 0%"
                    }
                }
                p {
                    attributes["id"] = "export-progress-text"
                    +"Processed 0 of 0 hikes"
                }
            }

            form(action = "/export/geojson", method = FormMethod.post) {
                attributes["id"] = "export-form"
                button(type = ButtonType.submit, classes = "btn btn-primary") {
                    +"Export to GeoJSONs"
                }
            }
        }
    }

    // Add JavaScript for export progress
    script(type = "text/javascript") {
        unsafe {
            +"""
            document.addEventListener('DOMContentLoaded', function() {
                const exportForm = document.getElementById('export-form');
                const progressContainer = document.getElementById('export-progress-container');
                const progressBar = document.getElementById('export-progress-bar');
                const progressText = document.getElementById('export-progress-text');

                if (exportForm) {
                    exportForm.addEventListener('submit', function(e) {
                        // Show progress bar when form is submitted
                        progressContainer.classList.remove('d-none');

                        // Start polling for progress
                        checkProgress();
                    });
                }

                function checkProgress() {
                    fetch('/api/export/progress')
                        .then(response => response.json())
                        .then(data => {
                            if (data.inProgress) {
                                // Update progress bar
                                const percent = data.total > 0 ? Math.round((data.current / data.total) * 100) : 0;
                                progressBar.style.width = percent + '%';
                                progressBar.setAttribute('aria-valuenow', percent);
                                progressText.textContent = 'Processed ' + data.current + ' of ' + data.total + ' hikes';

                                // Continue polling
                                setTimeout(checkProgress, 500);
                            } else if (data.total > 0 && data.current >= data.total) {
                                // Export completed
                                progressBar.style.width = '100%';
                                progressBar.setAttribute('aria-valuenow', 100);
                                progressText.textContent = 'Completed! Processed ' + data.current + ' of ' + data.total + ' hikes';
                            }
                        })
                        .catch(error => {
                            console.error('Error checking progress:', error);
                        });
                }

                // Check if an export is already in progress when page loads
                checkProgress();
            });
            """
        }
    }
}
