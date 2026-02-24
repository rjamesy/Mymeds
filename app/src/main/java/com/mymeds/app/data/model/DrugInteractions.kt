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
    Quad("diazepam", "morphine", "high",
        "Combined CNS depressants — risk of severe respiratory depression."),
    Quad("alprazolam", "morphine", "high",
        "Combined CNS depressants — risk of severe respiratory depression."),
    Quad("temazepam", "codeine", "high",
        "Combined CNS depressants — risk of severe respiratory depression."),
    Quad("temazepam", "oxycodone", "high",
        "Combined CNS depressants — risk of severe respiratory depression."),
    Quad("temazepam", "morphine", "high",
        "Combined CNS depressants — risk of severe respiratory depression."),
    Quad("lorazepam", "codeine", "high",
        "Combined CNS depressants — risk of severe respiratory depression."),
    Quad("lorazepam", "oxycodone", "high",
        "Combined CNS depressants — risk of severe respiratory depression."),
    Quad("lorazepam", "morphine", "high",
        "Combined CNS depressants — risk of severe respiratory depression."),
    Quad("diazepam", "fentanyl", "high",
        "Combined CNS depressants — risk of fatal respiratory depression."),
    Quad("alprazolam", "fentanyl", "high",
        "Combined CNS depressants — risk of fatal respiratory depression."),
    Quad("lorazepam", "fentanyl", "high",
        "Combined CNS depressants — risk of fatal respiratory depression."),
    Quad("temazepam", "fentanyl", "high",
        "Combined CNS depressants — risk of fatal respiratory depression."),

    // Benzodiazepines + alcohol
    Quad("diazepam", "alcohol", "high",
        "Combined CNS depressants — risk of excessive sedation and respiratory depression."),
    Quad("alprazolam", "alcohol", "high",
        "Combined CNS depressants — risk of excessive sedation and respiratory depression."),
    Quad("lorazepam", "alcohol", "high",
        "Combined CNS depressants — risk of excessive sedation and respiratory depression."),
    Quad("temazepam", "alcohol", "high",
        "Combined CNS depressants — risk of excessive sedation and respiratory depression."),

    // Opioids + alcohol
    Quad("codeine", "alcohol", "high",
        "Alcohol with opioids increases risk of respiratory depression and sedation."),
    Quad("oxycodone", "alcohol", "high",
        "Alcohol with opioids increases risk of respiratory depression and sedation."),
    Quad("morphine", "alcohol", "high",
        "Alcohol with opioids increases risk of respiratory depression and sedation."),
    Quad("tramadol", "alcohol", "high",
        "Alcohol with opioids increases risk of respiratory depression and seizures."),
    Quad("fentanyl", "alcohol", "high",
        "Alcohol with opioids — risk of fatal respiratory depression."),

    // DOACs (Direct Oral Anticoagulants)
    Quad("rivaroxaban", "aspirin", "high",
        "Increased bleeding risk. Avoid combining unless directed by doctor."),
    Quad("rivaroxaban", "ibuprofen", "high",
        "Increased bleeding risk. NSAIDs enhance anticoagulant effect."),
    Quad("rivaroxaban", "naproxen", "high",
        "Increased bleeding risk. NSAIDs enhance anticoagulant effect."),
    Quad("apixaban", "aspirin", "high",
        "Increased bleeding risk. Avoid combining unless directed by doctor."),
    Quad("apixaban", "ibuprofen", "high",
        "Increased bleeding risk. NSAIDs enhance anticoagulant effect."),
    Quad("apixaban", "naproxen", "high",
        "Increased bleeding risk. NSAIDs enhance anticoagulant effect."),
    Quad("dabigatran", "aspirin", "high",
        "Increased bleeding risk. Avoid combining unless directed by doctor."),
    Quad("dabigatran", "ibuprofen", "high",
        "Increased bleeding risk. NSAIDs enhance anticoagulant effect."),
    Quad("rivaroxaban", "ketoconazole", "high",
        "Ketoconazole significantly increases rivaroxaban levels. Avoid combination."),
    Quad("apixaban", "ketoconazole", "high",
        "Ketoconazole significantly increases apixaban levels. Avoid combination."),
    Quad("rivaroxaban", "sertraline", "moderate",
        "SSRIs may increase bleeding risk with anticoagulants."),
    Quad("apixaban", "sertraline", "moderate",
        "SSRIs may increase bleeding risk with anticoagulants."),
    Quad("dabigatran", "sertraline", "moderate",
        "SSRIs may increase bleeding risk with anticoagulants."),

    // Warfarin — additional
    Quad("warfarin", "fluconazole", "high",
        "Fluconazole significantly increases warfarin levels. High bleeding risk."),
    Quad("warfarin", "metronidazole", "high",
        "Metronidazole increases warfarin's anticoagulant effect. Monitor INR closely."),
    Quad("warfarin", "amiodarone", "high",
        "Amiodarone increases warfarin levels. Reduce warfarin dose and monitor INR."),
    Quad("warfarin", "cranberry", "moderate",
        "Cranberry juice may increase warfarin's effect. Monitor INR."),
    Quad("warfarin", "vitamin k", "moderate",
        "Vitamin K reduces warfarin effectiveness. Keep dietary intake consistent."),
    Quad("warfarin", "sertraline", "moderate",
        "SSRIs may increase bleeding risk with warfarin."),
    Quad("warfarin", "fluoxetine", "moderate",
        "SSRIs may increase bleeding risk with warfarin."),

    // More SSRI / SNRI interactions
    Quad("sertraline", "sumatriptan", "high",
        "Risk of serotonin syndrome. Both increase serotonin activity."),
    Quad("fluoxetine", "sumatriptan", "high",
        "Risk of serotonin syndrome. Both increase serotonin activity."),
    Quad("escitalopram", "sumatriptan", "high",
        "Risk of serotonin syndrome. Both increase serotonin activity."),
    Quad("escitalopram", "tramadol", "high",
        "Risk of serotonin syndrome. Both increase serotonin levels."),
    Quad("citalopram", "tramadol", "high",
        "Risk of serotonin syndrome. Both increase serotonin levels."),
    Quad("venlafaxine", "tramadol", "high",
        "Risk of serotonin syndrome. Both increase serotonin levels."),
    Quad("venlafaxine", "sumatriptan", "high",
        "Risk of serotonin syndrome. Both increase serotonin activity."),
    Quad("duloxetine", "tramadol", "high",
        "Risk of serotonin syndrome. Both increase serotonin levels."),
    Quad("duloxetine", "sumatriptan", "high",
        "Risk of serotonin syndrome. Both increase serotonin activity."),
    Quad("venlafaxine", "ibuprofen", "moderate",
        "SNRIs with NSAIDs increase gastrointestinal bleeding risk."),
    Quad("duloxetine", "ibuprofen", "moderate",
        "SNRIs with NSAIDs increase gastrointestinal bleeding risk."),
    Quad("fluoxetine", "metoprolol", "moderate",
        "Fluoxetine inhibits metoprolol metabolism. Risk of excessive heart rate lowering."),
    Quad("paroxetine", "metoprolol", "moderate",
        "Paroxetine inhibits metoprolol metabolism. Risk of excessive heart rate lowering."),
    Quad("paroxetine", "tramadol", "high",
        "Risk of serotonin syndrome. Both increase serotonin levels."),
    Quad("paroxetine", "ibuprofen", "moderate",
        "SSRIs with NSAIDs increase gastrointestinal bleeding risk."),
    Quad("paroxetine", "codeine", "moderate",
        "Paroxetine reduces codeine's conversion to morphine, reducing pain relief."),
    Quad("fluoxetine", "codeine", "moderate",
        "Fluoxetine reduces codeine's conversion to morphine, reducing pain relief."),

    // MAOIs (critical interactions)
    Quad("phenelzine", "sertraline", "high",
        "Contraindicated. Risk of fatal serotonin syndrome. 14-day washout required."),
    Quad("phenelzine", "fluoxetine", "high",
        "Contraindicated. Risk of fatal serotonin syndrome. 5-week washout for fluoxetine."),
    Quad("phenelzine", "venlafaxine", "high",
        "Contraindicated. Risk of fatal serotonin syndrome."),
    Quad("phenelzine", "tramadol", "high",
        "Contraindicated. Risk of fatal serotonin syndrome."),
    Quad("tranylcypromine", "sertraline", "high",
        "Contraindicated. Risk of fatal serotonin syndrome."),
    Quad("tranylcypromine", "fluoxetine", "high",
        "Contraindicated. Risk of fatal serotonin syndrome."),
    Quad("moclobemide", "sertraline", "high",
        "Risk of serotonin syndrome. Allow washout period between drugs."),
    Quad("moclobemide", "fluoxetine", "high",
        "Risk of serotonin syndrome. Allow washout period between drugs."),
    Quad("moclobemide", "venlafaxine", "high",
        "Risk of serotonin syndrome. Allow washout period between drugs."),

    // Tricyclic antidepressants
    Quad("amitriptyline", "tramadol", "high",
        "Risk of serotonin syndrome and increased sedation."),
    Quad("amitriptyline", "alcohol", "high",
        "Combined CNS depression — excessive drowsiness and impaired coordination."),
    Quad("amitriptyline", "codeine", "moderate",
        "Increased sedation and CNS depression."),
    Quad("nortriptyline", "tramadol", "high",
        "Risk of serotonin syndrome and increased sedation."),

    // Digoxin interactions
    Quad("digoxin", "amiodarone", "high",
        "Amiodarone increases digoxin levels. Risk of digoxin toxicity."),
    Quad("digoxin", "verapamil", "high",
        "Verapamil increases digoxin levels and slows heart rate excessively."),
    Quad("digoxin", "diltiazem", "moderate",
        "Diltiazem may increase digoxin levels. Monitor digoxin levels."),
    Quad("digoxin", "spironolactone", "moderate",
        "Spironolactone may increase digoxin levels. Monitor closely."),
    Quad("digoxin", "clarithromycin", "high",
        "Clarithromycin increases digoxin levels. Risk of toxicity."),
    Quad("digoxin", "erythromycin", "high",
        "Erythromycin increases digoxin levels. Risk of toxicity."),
    Quad("digoxin", "furosemide", "moderate",
        "Furosemide-induced low potassium increases risk of digoxin toxicity."),

    // Antifungals
    Quad("ketoconazole", "simvastatin", "high",
        "Ketoconazole greatly increases statin levels. Risk of severe muscle damage."),
    Quad("ketoconazole", "atorvastatin", "high",
        "Ketoconazole greatly increases statin levels. Risk of severe muscle damage."),
    Quad("fluconazole", "simvastatin", "high",
        "Fluconazole increases statin levels. Risk of muscle damage."),
    Quad("fluconazole", "atorvastatin", "moderate",
        "Fluconazole may increase atorvastatin levels. Monitor for muscle pain."),
    Quad("itraconazole", "simvastatin", "high",
        "Itraconazole greatly increases statin levels. Combination contraindicated."),
    Quad("fluconazole", "citalopram", "high",
        "Fluconazole increases citalopram levels. Risk of QT prolongation."),
    Quad("fluconazole", "escitalopram", "high",
        "Fluconazole increases escitalopram levels. Risk of QT prolongation."),

    // More statin interactions
    Quad("simvastatin", "diltiazem", "moderate",
        "Diltiazem increases simvastatin levels. Max simvastatin dose is 10mg."),
    Quad("simvastatin", "amiodarone", "high",
        "Amiodarone increases simvastatin levels. Max simvastatin dose is 20mg."),
    Quad("atorvastatin", "erythromycin", "high",
        "Erythromycin increases statin levels. Risk of muscle damage."),
    Quad("rosuvastatin", "gemfibrozil", "high",
        "Gemfibrozil increases rosuvastatin levels. Risk of severe muscle damage."),
    Quad("simvastatin", "gemfibrozil", "high",
        "Gemfibrozil increases simvastatin levels. Combination contraindicated."),
    Quad("atorvastatin", "gemfibrozil", "high",
        "Gemfibrozil increases statin levels. Risk of severe muscle damage."),

    // More ACE inhibitor / ARB interactions
    Quad("ramipril", "spironolactone", "moderate",
        "Both raise potassium levels. Monitor potassium closely."),
    Quad("enalapril", "spironolactone", "moderate",
        "Both raise potassium levels. Monitor potassium closely."),
    Quad("lisinopril", "ibuprofen", "moderate",
        "NSAIDs reduce ACE inhibitor effectiveness and may worsen kidney function."),
    Quad("ramipril", "ibuprofen", "moderate",
        "NSAIDs reduce ACE inhibitor effectiveness and may worsen kidney function."),
    Quad("enalapril", "ibuprofen", "moderate",
        "NSAIDs reduce ACE inhibitor effectiveness and may worsen kidney function."),
    Quad("losartan", "potassium", "high",
        "Risk of dangerously high potassium levels (hyperkalemia)."),
    Quad("losartan", "spironolactone", "moderate",
        "Both raise potassium levels. Monitor potassium closely."),
    Quad("losartan", "ibuprofen", "moderate",
        "NSAIDs reduce ARB effectiveness and may worsen kidney function."),
    Quad("candesartan", "potassium", "high",
        "Risk of dangerously high potassium levels (hyperkalemia)."),
    Quad("irbesartan", "potassium", "high",
        "Risk of dangerously high potassium levels (hyperkalemia)."),
    Quad("telmisartan", "potassium", "high",
        "Risk of dangerously high potassium levels (hyperkalemia)."),
    Quad("valsartan", "potassium", "high",
        "Risk of dangerously high potassium levels (hyperkalemia)."),
    Quad("valsartan", "spironolactone", "moderate",
        "Both raise potassium levels. Monitor potassium closely."),

    // More beta blocker interactions
    Quad("metoprolol", "diltiazem", "high",
        "Risk of severe slow heart rate and low blood pressure."),
    Quad("atenolol", "diltiazem", "high",
        "Risk of severe slow heart rate and low blood pressure."),
    Quad("propranolol", "verapamil", "high",
        "Risk of severe slow heart rate, low blood pressure, and heart failure."),
    Quad("propranolol", "diltiazem", "high",
        "Risk of severe slow heart rate and low blood pressure."),
    Quad("metoprolol", "clonidine", "moderate",
        "Stopping clonidine while on a beta blocker can cause rebound hypertension."),
    Quad("propranolol", "salbutamol", "moderate",
        "Beta blockers can reduce the effectiveness of salbutamol (bronchodilator)."),
    Quad("metoprolol", "salbutamol", "moderate",
        "Beta blockers can reduce the effectiveness of salbutamol (bronchodilator)."),
    Quad("atenolol", "salbutamol", "moderate",
        "Beta blockers can reduce the effectiveness of salbutamol (bronchodilator)."),

    // Diabetes — additional
    Quad("gliclazide", "fluconazole", "high",
        "Fluconazole increases gliclazide levels. Risk of severe hypoglycemia."),
    Quad("glipizide", "fluconazole", "high",
        "Fluconazole increases glipizide levels. Risk of severe hypoglycemia."),
    Quad("glimepiride", "fluconazole", "high",
        "Fluconazole increases glimepiride levels. Risk of severe hypoglycemia."),
    Quad("insulin", "metoprolol", "moderate",
        "Beta blockers can mask hypoglycemia symptoms (tremor, fast heart rate)."),
    Quad("insulin", "propranolol", "moderate",
        "Beta blockers can mask hypoglycemia symptoms (tremor, fast heart rate)."),
    Quad("metformin", "furosemide", "moderate",
        "Furosemide may increase metformin levels. Monitor kidney function."),

    // Anticonvulsants / antiepileptics
    Quad("carbamazepine", "warfarin", "high",
        "Carbamazepine reduces warfarin effectiveness. Monitor INR closely."),
    Quad("carbamazepine", "oral contraceptive", "high",
        "Carbamazepine reduces contraceptive effectiveness. Use alternative methods."),
    Quad("carbamazepine", "simvastatin", "moderate",
        "Carbamazepine reduces statin levels, decreasing effectiveness."),
    Quad("phenytoin", "warfarin", "high",
        "Complex interaction — may increase or decrease warfarin effect. Monitor INR."),
    Quad("phenytoin", "oral contraceptive", "high",
        "Phenytoin reduces contraceptive effectiveness. Use alternative methods."),
    Quad("valproate", "lamotrigine", "high",
        "Valproate doubles lamotrigine levels. Lamotrigine dose must be halved."),
    Quad("carbamazepine", "lamotrigine", "moderate",
        "Carbamazepine reduces lamotrigine levels. Dose adjustment needed."),
    Quad("carbamazepine", "erythromycin", "high",
        "Erythromycin increases carbamazepine to toxic levels."),
    Quad("carbamazepine", "clarithromycin", "high",
        "Clarithromycin increases carbamazepine to toxic levels."),
    Quad("phenytoin", "fluconazole", "high",
        "Fluconazole increases phenytoin levels. Risk of toxicity."),

    // More PPI interactions
    Quad("esomeprazole", "clopidogrel", "high",
        "Esomeprazole reduces the effectiveness of clopidogrel."),
    Quad("lansoprazole", "methotrexate", "moderate",
        "PPIs may increase methotrexate levels."),
    Quad("pantoprazole", "methotrexate", "moderate",
        "PPIs may increase methotrexate levels."),
    Quad("omeprazole", "iron", "moderate",
        "PPIs reduce iron absorption. Take iron separately."),
    Quad("esomeprazole", "iron", "moderate",
        "PPIs reduce iron absorption. Take iron separately."),
    Quad("omeprazole", "calcium", "moderate",
        "Long-term PPI use may reduce calcium absorption. Consider supplements."),

    // More levothyroxine interactions
    Quad("levothyroxine", "antacid", "moderate",
        "Antacids reduce levothyroxine absorption. Take 4 hours apart."),
    Quad("levothyroxine", "sucralfate", "moderate",
        "Sucralfate reduces levothyroxine absorption. Take 4 hours apart."),
    Quad("levothyroxine", "cholestyramine", "high",
        "Cholestyramine greatly reduces levothyroxine absorption. Take 4-6 hours apart."),
    Quad("levothyroxine", "carbamazepine", "moderate",
        "Carbamazepine increases levothyroxine metabolism. May need dose increase."),

    // More lithium interactions
    Quad("lithium", "ramipril", "high",
        "ACE inhibitors can increase lithium levels to toxic range."),
    Quad("lithium", "enalapril", "high",
        "ACE inhibitors can increase lithium levels to toxic range."),
    Quad("lithium", "losartan", "high",
        "ARBs can increase lithium levels. Monitor lithium closely."),
    Quad("lithium", "furosemide", "high",
        "Diuretics can increase lithium levels by reducing renal clearance."),
    Quad("lithium", "hydrochlorothiazide", "high",
        "Thiazide diuretics increase lithium levels. Risk of toxicity."),
    Quad("lithium", "carbamazepine", "moderate",
        "May increase neurotoxicity without affecting lithium levels. Monitor closely."),

    // More antibiotic interactions
    Quad("metronidazole", "alcohol", "high",
        "Severe nausea, vomiting, flushing, and headache (disulfiram-like reaction)."),
    Quad("ciprofloxacin", "calcium", "moderate",
        "Calcium reduces ciprofloxacin absorption. Take 2 hours apart."),
    Quad("ciprofloxacin", "iron", "moderate",
        "Iron reduces ciprofloxacin absorption. Take 2 hours apart."),
    Quad("ciprofloxacin", "antacid", "moderate",
        "Antacids reduce ciprofloxacin absorption. Take 2 hours apart."),
    Quad("ciprofloxacin", "warfarin", "high",
        "Ciprofloxacin increases warfarin's anticoagulant effect. Monitor INR."),
    Quad("doxycycline", "calcium", "moderate",
        "Calcium reduces doxycycline absorption. Take 2-3 hours apart."),
    Quad("doxycycline", "iron", "moderate",
        "Iron reduces doxycycline absorption. Take 2-3 hours apart."),
    Quad("doxycycline", "antacid", "moderate",
        "Antacids reduce doxycycline absorption. Take 2-3 hours apart."),
    Quad("doxycycline", "oral contraceptive", "moderate",
        "Doxycycline may reduce contraceptive effectiveness. Use backup method."),
    Quad("trimethoprim", "methotrexate", "high",
        "Both are folate antagonists. Risk of severe bone marrow suppression."),
    Quad("erythromycin", "simvastatin", "high",
        "Erythromycin increases statin levels. Risk of severe muscle damage."),
    Quad("erythromycin", "theophylline", "high",
        "Erythromycin increases theophylline levels. Risk of toxicity."),
    Quad("rifampicin", "warfarin", "high",
        "Rifampicin greatly reduces warfarin effectiveness. Major dose adjustment needed."),
    Quad("rifampicin", "oral contraceptive", "high",
        "Rifampicin renders oral contraceptives ineffective. Use alternative methods."),
    Quad("rifampicin", "simvastatin", "high",
        "Rifampicin greatly reduces statin levels. Statin will be ineffective."),

    // Corticosteroids
    Quad("prednisone", "ibuprofen", "moderate",
        "Increased risk of gastrointestinal bleeding and ulceration."),
    Quad("prednisone", "aspirin", "moderate",
        "Increased risk of gastrointestinal bleeding and ulceration."),
    Quad("prednisone", "naproxen", "moderate",
        "Increased risk of gastrointestinal bleeding and ulceration."),
    Quad("prednisone", "warfarin", "moderate",
        "Corticosteroids may alter warfarin effect. Monitor INR."),
    Quad("prednisolone", "ibuprofen", "moderate",
        "Increased risk of gastrointestinal bleeding and ulceration."),
    Quad("prednisolone", "aspirin", "moderate",
        "Increased risk of gastrointestinal bleeding and ulceration."),
    Quad("dexamethasone", "warfarin", "moderate",
        "Dexamethasone may alter warfarin effect. Monitor INR."),

    // Methotrexate — additional
    Quad("methotrexate", "ibuprofen", "high",
        "NSAIDs reduce methotrexate clearance. Risk of severe toxicity."),
    Quad("methotrexate", "naproxen", "high",
        "NSAIDs reduce methotrexate clearance. Risk of severe toxicity."),
    Quad("methotrexate", "aspirin", "high",
        "Aspirin reduces methotrexate clearance. Risk of toxicity."),
    Quad("methotrexate", "folic acid", "moderate",
        "Folic acid is commonly co-prescribed to reduce methotrexate side effects. Take as directed."),

    // Potassium-sparing diuretics
    Quad("spironolactone", "potassium", "high",
        "Risk of dangerously high potassium levels (hyperkalemia)."),
    Quad("amiloride", "potassium", "high",
        "Risk of dangerously high potassium levels (hyperkalemia)."),
    Quad("spironolactone", "trimethoprim", "high",
        "Both raise potassium levels. Risk of hyperkalemia."),

    // Antipsychotics
    Quad("quetiapine", "alcohol", "high",
        "Combined CNS depression — excessive sedation and impaired coordination."),
    Quad("quetiapine", "codeine", "moderate",
        "Increased sedation and CNS depression."),
    Quad("olanzapine", "alcohol", "high",
        "Combined CNS depression — excessive sedation and impaired coordination."),
    Quad("olanzapine", "diazepam", "high",
        "Combined CNS depression — risk of severe sedation and respiratory depression."),
    Quad("haloperidol", "lithium", "moderate",
        "Risk of increased neurotoxicity. Monitor for confusion and tremor."),
    Quad("risperidone", "carbamazepine", "moderate",
        "Carbamazepine reduces risperidone levels. May need dose adjustment."),

    // Amiodarone — additional
    Quad("amiodarone", "metoprolol", "high",
        "Risk of severe slow heart rate and cardiac conduction problems."),
    Quad("amiodarone", "diltiazem", "high",
        "Risk of severe slow heart rate, low blood pressure, and cardiac arrest."),
    Quad("amiodarone", "verapamil", "high",
        "Risk of severe slow heart rate, low blood pressure, and cardiac arrest."),
    Quad("amiodarone", "rivaroxaban", "moderate",
        "Amiodarone may increase rivaroxaban levels. Monitor for bleeding."),

    // Clopidogrel — additional
    Quad("clopidogrel", "aspirin", "moderate",
        "Increased bleeding risk, but often prescribed together under medical supervision."),
    Quad("clopidogrel", "ibuprofen", "moderate",
        "NSAIDs increase bleeding risk with clopidogrel."),
    Quad("clopidogrel", "naproxen", "moderate",
        "NSAIDs increase bleeding risk with clopidogrel."),

    // Allopurinol (gout)
    Quad("allopurinol", "azathioprine", "high",
        "Allopurinol greatly increases azathioprine toxicity. Dose must be reduced 75%."),
    Quad("allopurinol", "mercaptopurine", "high",
        "Allopurinol greatly increases mercaptopurine toxicity. Dose must be reduced 75%."),
    Quad("allopurinol", "warfarin", "moderate",
        "Allopurinol may increase warfarin effect. Monitor INR."),

    // Supplements
    Quad("iron", "calcium", "moderate",
        "Calcium reduces iron absorption. Take at least 2 hours apart."),
    Quad("iron", "antacid", "moderate",
        "Antacids reduce iron absorption. Take at least 2 hours apart."),
    Quad("magnesium", "ciprofloxacin", "moderate",
        "Magnesium reduces ciprofloxacin absorption. Take 2 hours apart."),
    Quad("magnesium", "doxycycline", "moderate",
        "Magnesium reduces doxycycline absorption. Take 2-3 hours apart."),
    Quad("st john's wort", "sertraline", "high",
        "Risk of serotonin syndrome. Both increase serotonin activity."),
    Quad("st john's wort", "fluoxetine", "high",
        "Risk of serotonin syndrome. Both increase serotonin activity."),
    Quad("st john's wort", "oral contraceptive", "high",
        "St John's Wort reduces contraceptive effectiveness."),
    Quad("st john's wort", "warfarin", "high",
        "St John's Wort reduces warfarin effectiveness. Risk of clotting."),
    Quad("st john's wort", "simvastatin", "moderate",
        "St John's Wort reduces statin levels, decreasing effectiveness."),
    Quad("st john's wort", "rivaroxaban", "high",
        "St John's Wort reduces rivaroxaban levels. Risk of clotting."),
    Quad("st john's wort", "apixaban", "high",
        "St John's Wort reduces apixaban levels. Risk of clotting."),

    // Sildenafil / PDE5 inhibitors
    Quad("sildenafil", "nitrate", "high",
        "Contraindicated. Combined use can cause fatal drop in blood pressure."),
    Quad("sildenafil", "nitroglycerin", "high",
        "Contraindicated. Combined use can cause fatal drop in blood pressure."),
    Quad("sildenafil", "isosorbide", "high",
        "Contraindicated. Combined use can cause fatal drop in blood pressure."),
    Quad("tadalafil", "nitrate", "high",
        "Contraindicated. Combined use can cause fatal drop in blood pressure."),
    Quad("tadalafil", "nitroglycerin", "high",
        "Contraindicated. Combined use can cause fatal drop in blood pressure."),
    Quad("tadalafil", "isosorbide", "high",
        "Contraindicated. Combined use can cause fatal drop in blood pressure."),
    Quad("sildenafil", "amlodipine", "moderate",
        "Additive blood pressure lowering. Monitor for dizziness/low BP."),
    Quad("tadalafil", "amlodipine", "moderate",
        "Additive blood pressure lowering. Monitor for dizziness/low BP."),

    // QT prolongation risks
    Quad("citalopram", "amiodarone", "high",
        "Both prolong QT interval. Risk of dangerous heart rhythm."),
    Quad("escitalopram", "amiodarone", "high",
        "Both prolong QT interval. Risk of dangerous heart rhythm."),
    Quad("haloperidol", "amiodarone", "high",
        "Both prolong QT interval. Risk of dangerous heart rhythm."),
    Quad("domperidone", "erythromycin", "high",
        "Both prolong QT interval. Risk of dangerous heart rhythm."),
    Quad("domperidone", "fluconazole", "high",
        "Both prolong QT interval. Risk of dangerous heart rhythm."),
)
