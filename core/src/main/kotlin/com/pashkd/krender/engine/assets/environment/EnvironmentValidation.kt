package com.pashkd.krender.engine.assets.environment

/**
 * Result of validating an [EnvironmentAsset].
 */
data class EnvironmentValidationReport(
    val status: ValidationStatus,
    val issues: List<EnvironmentIssue> = emptyList(),
)

/**
 * Overall validation status.
 */
enum class ValidationStatus {
    Valid,
    Warning,
    Error,
}

/**
 * A single validation issue found in an environment asset.
 */
data class EnvironmentIssue(
    val severity: IssueSeverity,
    val code: String,
    val message: String,
    val relatedPath: String? = null,
)

/**
 * Severity of a validation issue.
 */
enum class IssueSeverity {
    Info,
    Warning,
    Error,
}
