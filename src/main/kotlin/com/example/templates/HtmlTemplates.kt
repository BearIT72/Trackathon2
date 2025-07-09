package com.example.templates

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
