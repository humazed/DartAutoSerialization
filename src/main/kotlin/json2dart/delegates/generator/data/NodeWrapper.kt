package json2dart.delegates.generator.data

import com.fasterxml.jackson.databind.JsonNode
import json2dart.delegates.generator.toClassName
import json2dart.delegates.generator.toSneakCase

data class NodeWrapper(
    val node: JsonNode?,
    val fieldName: String,
    val sneakCaseName: String = toSneakCase(fieldName),
    val className: String = toClassName(fieldName)
)