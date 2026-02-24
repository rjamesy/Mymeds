package com.mymeds.app.data.model

/**
 * Simple local medication interactions database.
 * Maps common medication generic names (lowercase) to a list of medications
 * they may interact with, along with a brief description.
 *
 * This is NOT a substitute for professional medical advice. It covers only the
 * most common/well-known interactions as a convenience.
 */

data class InteractionWarning(
    val drug1: String,
    val drug2: String,
    val severity: String, // "high", "moderate", "low"
    val description: String
)

/**
 * Check for interactions between a list of active medication names.
 * Returns a list of warnings for any known interactions found.
 */
fun checkInteractions(medicationNames: List<String>): List<InteractionWarning> {
    if (medicationNames.size < 2) return emptyList()

    val normalised = medicationNames.map { it.lowercase().trim() }
    val warnings = mutableListOf<InteractionWarning>()
    val seen = mutableSetOf<String>()

    for (i in normalised.indices) {
        for (j in i + 1 until normalised.size) {
            val name1 = normalised[i]
            val name2 = normalised[j]
            val key = if (name1 < name2) "$name1|$name2" else "$name2|$name1"
            if (key in seen) continue
            seen.add(key)

            val interaction = findInteraction(name1, name2)
            if (interaction != null) {
                warnings.add(interaction.copy(
                    drug1 = medicationNames[i],
                    drug2 = medicationNames[j]
                ))
            }
        }
    }

    return warnings.sortedByDescending {
        when (it.severity) {
            "high" -> 3
            "moderate" -> 2
            else -> 1
        }
    }
}

private fun findInteraction(name1: String, name2: String): InteractionWarning? {
    for (entry in KNOWN_INTERACTIONS) {
        val entryDrug1 = entry.first.lowercase()
        val entryDrug2 = entry.second.lowercase()

        val match = (name1.contains(entryDrug1) && name2.contains(entryDrug2)) ||
                (name1.contains(entryDrug2) && name2.contains(entryDrug1))

        if (match) {
            return InteractionWarning(
                drug1 = name1,
                drug2 = name2,
                severity = entry.third,
                description = entry.fourth
            )
        }
    }
    return null
}

/**
 * Quadruple helper for interaction entries.
 */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Known drug interactions: (drug1, drug2, severity, description).
 * Uses generic names. Matches by substring so "Metformin 500mg" will match "metformin".
 */
private val KNOWN_INTERACTIONS: List<Quad<String, String, String, String>> = listOf(
    // Blood thinners
    Quad("warfarin", "aspirin", "high",
        "Increased bleeding risk. Both are blood thinners."),
    Quad("warfarin", "ibuprofen", "high",
        "Increased bleeding risk. NSAIDs can enhance warfarin's anticoagulant effect."),
    Quad("warfarin", "naproxen", "high",
        "Increased bleeding risk. NSAIDs can enhance warfarin's anticoagulant effect."),
    Quad("warfarin", "paracetamol", "moderate",
        "High doses of paracetamol may increase warfarin's effect."),
    Quad("warfarin", "acetaminophen", "moderate",
        "High doses of acetaminophen may increase warfarin's effect."),

    // NSAIDs combinations
    Quad("aspirin", "ibuprofen", "moderate",
        "Ibuprofen may reduce aspirin's cardioprotective effect and increase bleeding risk."),
    Quad("aspirin", "naproxen", "moderate",
        "Combined NSAID use increases gastrointestinal bleeding risk."),

    // Diabetes medications
    Quad("metformin", "alcohol", "moderate",
        "Alcohol can increase the risk of lactic acidosis with metformin."),
    Quad("metformin", "contrast", "high",
        "Metformin should be stopped before IV contrast dye procedures."),

    // ACE inhibitors + potassium
    Quad("lisinopril", "potassium", "high",
        "Risk of dangerously high potassium levels (hyperkalemia)."),
    Quad("enalapril", "potassium", "high",
        "Risk of dangerously high potassium levels (hyperkalemia)."),
    Quad("ramipril", "potassium", "high",
        "Risk of dangerously high potassium levels (hyperkalemia)."),
    Quad("lisinopril", "spironolactone", "moderate",
        "Both raise potassium levels. Monitor potassium closely."),

    // Statins
    Quad("simvastatin", "amlodipine", "moderate",
        "Amlodipine can increase simvastatin levels. Max dose of simvastatin is 20mg."),
    Quad("atorvastatin", "clarithromycin", "high",
        "Clarithromycin increases statin levels, raising risk of muscle damage."),
    Quad("simvastatin", "clarithromycin", "high",
        "Clarithromycin increases statin levels, raising risk of muscle damage."),

    // SSRIs
    Quad("sertraline", "tramadol", "high",
        "Risk of serotonin syndrome. Both increase serotonin levels."),
    Quad("fluoxetine", "tramadol", "high",
        "Risk of serotonin syndrome. Both increase serotonin levels."),
    Quad("sertraline", "ibuprofen", "moderate",
        "SSRIs with NSAIDs increase gastrointestinal bleeding risk."),
    Quad("fluoxetine", "ibuprofen", "moderate",
        "SSRIs with NSAIDs increase gastrointestinal bleeding risk."),
    Quad("escitalopram", "ibuprofen", "moderate",
        "SSRIs with NSAIDs increase gastrointestinal bleeding risk."),
    Quad("citalopram", "ibuprofen", "moderate",
        "SSRIs with NSAIDs increase gastrointestinal bleeding risk."),

    // Omeprazole / PPI interactions
    Quad("omeprazole", "clopidogrel", "high",
        "Omeprazole reduces the effectiveness of clopidogrel."),
    Quad("omeprazole", "methotrexate", "moderate",
        "PPIs may increase methotrexate levels."),

    // Beta blockers + calcium channel blockers
    Quad("metoprolol", "verapamil", "high",
        "Risk of severe slow heart rate and low blood pressure."),
    Quad("atenolol", "verapamil", "high",
        "Risk of severe slow heart rate and low blood pressure."),

    // Levothyroxine interactions
    Quad("levothyroxine", "calcium", "moderate",
        "Calcium supplements can reduce levothyroxine absorption. Take 4 hours apart."),
    Quad("levothyroxine", "iron", "moderate",
        "Iron supplements reduce levothyroxine absorption. Take 4 hours apart."),
    Quad("levothyroxine", "omeprazole", "moderate",
        "PPIs may reduce levothyroxine absorption."),

    // Lithium interactions
    Quad("lithium", "ibuprofen", "high",
        "NSAIDs can increase lithium levels to toxic range."),
    Quad("lithium", "naproxen", "high",
        "NSAIDs can increase lithium levels to toxic range."),
    Quad("lithium", "lisinopril", "high",
        "ACE inhibitors can increase lithium levels."),

    // Antibiotics
    Quad("amoxicillin", "methotrexate", "moderate",
        "Amoxicillin may increase methotrexate levels."),
    Quad("ciprofloxacin", "theophylline", "high",
        "Ciprofloxacin increases theophylline levels, risk of toxicity."),
    Quad("ciprofloxacin", "tizanidine", "high",
        "Ciprofloxacin greatly increases tizanidine levels. Combination contraindicated."),

    // Common OTC
    Quad("paracetamol", "alcohol", "moderate",
        "Regular alcohol use with paracetamol increases liver damage risk."),
    Quad("acetaminophen", "alcohol", "moderate",
        "Regular alcohol use with acetaminophen increases liver damage risk."),

    // Benzodiazepines + opioids
    Quad("diazepam", "codeine", "high",
        "Combined CNS depressants — risk of severe respiratory depression."),
    Quad("diazepam", "oxycodone", "high",
        "Combined CNS depressants — risk of severe respiratory depression."),
    Quad("alprazolam", "codeine", "high",
        "Combined CNS depressants — risk of severe respiratory depression."),
    Quad("alprazolam", "oxycodone", "high",
        "Combined CNS depressants — risk of severe respiratory depression."),
)
