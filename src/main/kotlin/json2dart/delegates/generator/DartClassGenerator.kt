package json2dart.delegates.generator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import json2dart.delegates.generator.data.NodeInfo
import json2dart.delegates.generator.data.NodeWrapper
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.util.*


class DartClassGenerator {

    fun generateFromJson(source: String, destiny: File, rootName: String, isFinal: Boolean) {
        val nodesToProcessStack = Stack<NodeWrapper>()

        try {
            nodesToProcessStack.add(
                NodeWrapper(
                    node = jacksonObjectMapper().readTree(source),
                    fieldName = rootName,
                    sneakCaseName = rootName,
                    className = extractRootClassName(rootName)
                )
            )
        } catch (e: Exception) {
            throw SyntaxException()
        }

        val packageTemplate = extractPackageName(destiny)
        val finalMode = if (isFinal) "final " else ""
        var nodeWrapper: NodeWrapper
        var nodeInfo: NodeInfo
        var buffer: FileOutputStream
        var target: FileOutputStream
        var constructorStringBuilder: StringBuilder
        var serializatorStringBuilder: StringBuilder
        val importsList = mutableListOf<String>()
        var bufferFile: File

        while (nodesToProcessStack.isNotEmpty()) {
            nodeWrapper = nodesToProcessStack.pop()
            bufferFile = File(destiny, "__${nodeWrapper.sneakCaseName}.dart")
            buffer = FileOutputStream(bufferFile)
            target = FileOutputStream(File(destiny, "${nodeWrapper.sneakCaseName}.dart"))
            constructorStringBuilder = createConstructorStart(nodeWrapper)
            serializatorStringBuilder = createSerializatorStart()


            buffer.writeText("\n@JsonSerializable()\nclass ${nodeWrapper.className} {\n")
            try {
                target.writeText("library ${nodeWrapper.sneakCaseName};\n\n")

                nodeWrapper.node?.fields()?.forEach {
                    nodeInfo = processNode(buffer, it.value, it.key, finalMode)

                    nodeInfo.node?.apply {
                        nodesToProcessStack.add(this)
                        target.writeText("import '$packageTemplate$sneakCaseName.dart';\n")
                    }

//                    serializatorStringBuilder.append(nodeInfo.mapSerialization)
//                    constructorStringBuilder.append("\t\t${it.key} = ${nodeInfo.mapDeserialization}")
                }

                constructorStringBuilder.apply {
                    deleteCharAt(length - 1).deleteCharAt(length - 1).append(";\n")
                }
//                serializatorStringBuilder
//                    .append("\t\treturn data;\n")
//                    .append("\t}\n")

//                buffer.writeText(constructorStringBuilder.toString()).writeText("\n")
//                buffer.writeText(serializatorStringBuilder.toString())

                target.writeText("\nimport 'package:json_annotation/json_annotation.dart';\n")
                target.writeText("\npart '${nodeWrapper.sneakCaseName}.g.dart';\n")

                buffer.writeText("\n\tfactory ${nodeWrapper.className}.fromJson(Map<String, dynamic> json) => _$${nodeWrapper.className}FromJson(json);\n")
                buffer.writeText("\n\tMap<String, dynamic> toJson() => _$${nodeWrapper.className}ToJson(this);\n")

                buffer.writeText("\n\tstatic const fromJsonFactory = _$${nodeWrapper.className}FromJson;\n")

                buffer.writeText("\n\t${nodeWrapper.className}();\n")

                buffer.writeText("\n  @override\n  String toString() {\n\t\treturn '${nodeWrapper.className}{")
                val toStringContent =
                    nodeWrapper.node?.fields()?.asSequence()?.toList()?.map { "${it.key}: $${it.key}" }
                        ?.joinToString(", ")
                buffer.writeText(toStringContent ?: "")
                buffer.writeText("}';\t\n\t}\n")

                buffer.writeText("}")
                buffer.close()

                mergeBufferAndTarget(target, bufferFile)
            } finally {
                buffer.close()
                target.close()
            }
            importsList.clear()
        }
    }

    private fun processNode(
        fout: FileOutputStream, node: JsonNode, name: String, finalMode: String
    ): NodeInfo {
        val nodeInfo = extractNodeInfo(node, name)
        fout.writeText("  $finalMode${nodeInfo.stringRepresentation} $name;\n")
        return nodeInfo
    }

    private fun extractNodeInfo(node: JsonNode, name: String): NodeInfo {
        return when {
            node.isDouble || node.isFloat || node.isBigDecimal ->
                NodeInfo("double", name)

            node.isShort || node.isInt || node.isLong || node.isBigInteger ->
                NodeInfo("int", name)

            node.isBoolean ->
                NodeInfo("bool", name)

            node.isTextual ->
                NodeInfo("String", name)

            node.isArray ->
                extractArrayData(node as ArrayNode, name)

            node.isObject ->
                NodeWrapper(node, name).toObjectNodeInfo()

            else -> NodeInfo("Object", name)
        }
    }

    private fun extractArrayData(node: ArrayNode, name: String): NodeInfo {
        val iterator = node.iterator()
        if (!iterator.hasNext()) {
            return NodeInfo("List<Object>", name)
        }
        val elementInfo = extractNodeInfo(iterator.next(), name)
        return NodeInfo(
            "List<${elementInfo.stringRepresentation}>",
            elementInfo.node,
            elementInfo.buildListDeserialization(name),
            elementInfo.buildListSerialization(name)
        )
    }

    private fun createConstructorStart(nodeWrapper: NodeWrapper) =
        StringBuilder()
            .append("\n\tfactory ${nodeWrapper.className}.fromJson(Map<String, dynamic> map) =>_$${nodeWrapper.className}(json); \n")


    private fun createSerializatorStart() =
        StringBuilder()
            .append("\tMap<String, dynamic> toJson() {\n")
            .append("\t\tfinal Map<String, dynamic> data = new Map<String, dynamic>();\n")

    private fun mergeBufferAndTarget(targetStream: FileOutputStream, bufferFile: File) {
        BufferedReader(FileReader(bufferFile)).useLines { lines ->
            lines.forEach {
                targetStream.writeText(it).writeText("\n")
            }
        }

        bufferFile.delete()
    }

    private fun extractPackageName(dir: File): String {
        val absolutePath = dir.absolutePath
        val splitted = absolutePath.split(if (isWindows()) "\\" else "/")
        val libIndex = splitted.indexOf("lib")
        if (libIndex == -1) {
            throw NotAFlutterProject()
        }
        val fold = splitted
            .subList(libIndex + 1, splitted.size)
            .fold(StringBuilder()) { builder, s -> builder.append(s).append("/") }
        return "package:${splitted[libIndex - 1]}/$fold"
    }

    private fun FileOutputStream.writeText(text: String): FileOutputStream {
        write(text.toByteArray(Charsets.UTF_8))
        return this
    }

    private fun NodeWrapper.toObjectNodeInfo(): NodeInfo {
        val field = this.fieldName
        return NodeInfo(
            className,
            this,
            "$className.fromJsonMap(map[\"$fieldName\"]),\n",
            "\t\tdata['$field'] = $field == null ? null : $field.toJson();\n"
        )
    }

    private fun NodeInfo.buildListDeserialization(rawName: String) =
        if (node != null) {
            "List<${node.className}>.from(map[\"${node.fieldName}\"]" +
                    ".map((it) => ${node.className}.fromJsonMap(it))),\n"
        } else {
            "List<$stringRepresentation>.from(map[\"$rawName\"]),\n"
        }

    private fun NodeInfo.buildListSerialization(rawName: String) =
        if (node != null) {
            "\t\tdata['$rawName'] = ${node.fieldName} != null ? \n" +
                    "\t\t\tthis.${node.fieldName}.map((v) => v.toJson()).toList()\n" +
                    "\t\t\t: null;\n"
        } else {
            "\t\tdata['$rawName'] = $rawName;\n"
        }


    private fun extractRootClassName(rootFileName: String): String {
        var needUp = true
        val builder = StringBuilder()
        val i = rootFileName.iterator()
        var element: Char

        while (i.hasNext()) {
            element = i.nextChar()
            if (element == '_') {
                needUp = true
                continue
            }
            if (needUp) {
                element = element.toUpperCase()
                needUp = false
            }

            builder.append(element)
        }
        return builder.toString()
    }
}