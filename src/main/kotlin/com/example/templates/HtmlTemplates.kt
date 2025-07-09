package com.example.templates

import io.ktor.server.html.*
import kotlinx.html.*

class IndexPage : Template<HTML> {
    val content = Placeholder<FlowContent>()
    
    override fun HTML.apply() {
        head {
            title("Ktor Web App")
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css")
        }
        body {
            div("container mt-5") {
                insert(content)
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
    div("mt-4") {
        a(href = "/api/health", classes = "btn btn-primary") {
            +"Check API Health"
        }
    }
}