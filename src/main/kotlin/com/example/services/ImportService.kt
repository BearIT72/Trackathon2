package com.example.services

import com.example.models.*
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Service class for handling import-related operations
 */
class ImportService {
    /**
     * Import data from CSV file
     * @return ImportResult with the count of imported records
     */
    fun importDataFromCsv(): ImportResult {
        val file = File("input/flat/id_geojson.csv")
        if (!file.exists()) {
            throw IllegalStateException("CSV file not found at ${file.absolutePath}")
        }

        var importCount = 0

        transaction {
            CSVParser.parse(file, StandardCharsets.UTF_8, CSVFormat.DEFAULT).use { csvParser ->
                for (record in csvParser) {
                    if (record.size() >= 2) {
                        val hikeId = record.get(0)
                        val geoJson = record.get(1)

                        // Check if record already exists
                        val existingRecord = InputDataEntity.find { InputDataTable.hikeId eq hikeId }.firstOrNull()

                        if (existingRecord == null) {
                            // Create new record
                            InputDataEntity.new {
                                this.hikeId = hikeId
                                this.geoJson = geoJson
                            }
                            importCount++
                        }
                    }
                }
            }
        }

        return ImportResult(importCount)
    }

    /**
     * Purge all data from the database
     * @return The number of records purged
     */
    fun purgeAllData(): Long {
        var purgeCount: Long = 0
        transaction {
            purgeCount = InputDataEntity.all().count()
            InputDataEntity.all().forEach { it.delete() }
        }
        return purgeCount
    }

    /**
     * Get the count of input data records
     * @return The number of records in the database
     */
    fun getInputDataCount(): Long {
        return transaction {
            InputDataEntity.all().count()
        }
    }
}

/**
 * Data class for import result
 */
data class ImportResult(val count: Int)