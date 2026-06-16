package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.*

object BindingDefinitionValidator : UiSceneValidationRule {
    override val id: String = "BindingDefinitionValidator"

    override fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue> {
        val issues = mutableListOf<UiSceneValidationIssue>()
        val seenKeys = mutableSetOf<String>()
        context.document.bindings.forEach { binding ->
            when {
                binding.key.isBlank() ->
                    issues +=
                        error(
                            code = UiSceneValidationCode.BlankBindingKey,
                            message = "Binding key must not be blank.",
                            fieldName = "bindings.key",
                        )

                !seenKeys.add(binding.key) ->
                    issues +=
                        error(
                            code = UiSceneValidationCode.DuplicateBindingKey,
                            message = "Binding key '${binding.key}' is duplicated within this document.",
                            fieldName = "bindings.key",
                            bindingKey = binding.key,
                        )
            }
            when (binding.type) {
                UiSceneBindingType.Number -> {
                    if (binding.defaultValue.toFloatOrNull() == null) {
                        issues +=
                            error(
                                code = UiSceneValidationCode.InvalidNumberBindingDefault,
                                message = "Number binding '${binding.key}' defaultValue must parse as Float.",
                                fieldName = "bindings.defaultValue",
                                bindingKey = binding.key.takeIf(String::isNotBlank),
                            )
                    }
                }

                UiSceneBindingType.Texture -> {
                    if (binding.defaultValue.isBlank()) {
                        issues +=
                            warning(
                                code = UiSceneValidationCode.BlankTextureBindingDefault,
                                message = "Texture binding '${binding.key}' defaultValue is blank.",
                                fieldName = "bindings.defaultValue",
                                bindingKey = binding.key.takeIf(String::isNotBlank),
                            )
                    }
                }

                UiSceneBindingType.Action -> {
                    if (binding.defaultValue.isBlank()) {
                        issues +=
                            warning(
                                code = UiSceneValidationCode.BlankActionBindingDefault,
                                message = "Action binding '${binding.key}' defaultValue is blank.",
                                fieldName = "bindings.defaultValue",
                                bindingKey = binding.key.takeIf(String::isNotBlank),
                            )
                    }
                }

                UiSceneBindingType.Text -> Unit
            }
        }
        return issues
    }
}
