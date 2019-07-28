package json2dart.delegates.generator

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import json2dart.delegates.MessageDelegate
import java.io.File
import java.io.IOException

class GeneratorDelegate(
    private val messageDelegate: MessageDelegate = MessageDelegate()
) {

    fun runGeneration(
        fileName: String,
        json: String,
        finalFields: Boolean,
        destinyVf: VirtualFile?,
        project: Project?
    ) {
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project, "Dart file generating", false
            ) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        destinyVf?.let {
                            DartClassGenerator().generateFromJson(
                                json,
                                File(it.path),
                                fileName.takeIf { it.isNotBlank() } ?: "response",
                                finalFields
                            )
                        }
//                        messageDelegate.showMessage("Dart class has been generated")
                    } catch (e: Throwable) {
                        when (e) {
                            is IOException -> messageDelegate.onException(FileIOException())
                            else -> messageDelegate.onException(e)
                        }
                    } finally {
                        indicator.stop()
                        ProjectView.getInstance(project).refresh()
                        destinyVf?.refresh(false, true)
                    }
                }
            }
        )
    }
}