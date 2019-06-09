package json2dart


import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.DialogBuilder
import json2dart.delegates.generator.GeneratorDelegate
import json2dart.view.Json2DartForm

class JsonToDartAction(
    private val generatorDelegate: GeneratorDelegate = GeneratorDelegate()
) : AnAction("Convert json to dart") {

    override fun actionPerformed(event: AnActionEvent) {
        DialogBuilder().apply {
            val form = Json2DartForm()
            form.setOnGenerateListener { fileName, json, finalFields ->
                window.dispose()
                generatorDelegate.runGeneration(event, fileName, json, finalFields)
            }
            setCenterPanel(form.rootView)
            setTitle("Json2Dart")
            removeAllActions()
            show()
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.VIRTUAL_FILE)?.isDirectory ?: false
    }
}