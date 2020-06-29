package org.elm.ide.actions

import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.Topic
import org.elm.ide.notifications.showBalloon
import org.elm.lang.core.ElmFileType
import org.elm.lang.core.psi.ElmFile
import org.elm.lang.core.psi.isElmFile
import org.elm.openapiext.isUnitTestMode
import org.elm.openapiext.pathAsPath
import org.elm.workspace.*
import org.elm.workspace.commandLineTools.ElmReviewCLI
import org.elm.workspace.compiler.*
import java.nio.file.Path

private val log = logger<ElmExternalReviewAction>()

class ElmExternalReviewAction : AnAction() {

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = getContext(e) != null
    }

    private fun showError(project: Project, message: String, includeFixAction: Boolean = false) {
        val actions = if (includeFixAction)
            arrayOf("Fix" to { project.elmWorkspace.showConfigureToolchainUI() })
        else
            emptyArray()
        project.showBalloon(message, NotificationType.ERROR, *actions)
    }

    private fun findActiveFile(e: AnActionEvent, project: Project): VirtualFile? =
            e.getData(CommonDataKeys.VIRTUAL_FILE)
                    ?: FileEditorManager.getInstance(project).selectedFiles.firstOrNull { it.fileType == ElmFileType }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val elmReviewCLI = project.elmToolchain.elmReviewCLI
                ?: return showError(project, "Please set the path to the 'elm-review' binary", includeFixAction = true)

        val activeFile = findActiveFile(e, project)
                ?: return showError(project, "Could not determine active Elm file")

        val elmProject = project.elmWorkspace.findProjectForFile(activeFile)
                ?: return showError(project, "Could not determine active Elm project")

        val projectDir = VfsUtil.findFile(elmProject.projectDirPath, true)
                ?: return showError(project, "Could not determine active Elm project's path")

//        val (targetPath) = when (elmProject) {
//            is ElmApplicationProject -> {
//                val mainEntryPoint = findMainEntryPoint(project, elmProject)
//                mainEntryPoint?.containingFile?.virtualFile?.let { Triple(it.pathAsPath, VfsUtilCore.getRelativePath(it, projectDir), mainEntryPoint.textOffset) }
//                        ?: return showError(project, "Cannot find your Elm app's main entry point. Please make sure that it has a type annotation.")
//            }
//
//            is ElmPackageProject ->
//                Triple(activeFile.pathAsPath, VfsUtilCore.getRelativePath(activeFile, projectDir), 0)
//        }

        showError(project, "elmProject $elmProject")
        showError(project, "projectDir $projectDir")
        val ctx = getContext(e)
        showError(project, "ctx $ctx")

        if (ctx == null) {
            if (isUnitTestMode) error("should not happen: context is null!")
            return
        }

        val fixAction = "Fix" to { ctx.project.elmWorkspace.showConfigureToolchainUI() }

        val elmReview = ctx.project.elmToolchain.elmReviewCLI
        showError(project, "elmReview $elmReview")
        if (elmReview == null) {
            showError(project, "Could not find elm-review")
            ctx.project.showBalloon("Could not find elm-review", NotificationType.ERROR, fixAction)
            return
        }

        val json = try {
            elmReviewCLI.runReview().stdout
        } catch (e: ExecutionException) {
            showError(project, "execution error $e")
            return
        }
        showError(project, "json $json")

        val messages = if (json.isEmpty()) emptyList() else {
//            listOf(ElmError(
//                    title = report.title,
//                    html = chunksToHtml(report.message),
//                    location = report.path?.let { ElmLocation(path = it, moduleName = null, region = null) }))
            listOf(ElmError(
                    title = "Some hardcoded error",
                    html = "<h1>Some error occurred</h1>",
                    location = null // report.path?.let { ElmLocation(path = it, moduleName = null, region = null) }))
            ))
//            elmJsonToCompilerMessages(json).sortedWith(
//                    compareBy(
//                            { it.location?.moduleName },
//                            { it.location?.region?.start?.line },
//                            { it.location?.region?.start?.column }
//                    ))
        }
        project.messageBus.syncPublisher(ElmBuildAction.ERRORS_TOPIC).update(elmProject.projectDirPath, messages, null, 0)
        if (isUnitTestMode) return
        ToolWindowManager.getInstance(project).getToolWindow("Elm Compiler").show(null)
//        if (result.isSuccess) {
//            val formatted = result.stdout
//            val source = document.text
//
//            if (source != formatted) {
//
//                val writeAction = {
//                    ApplicationManager.getApplication().runWriteAction {
//                        document.setText(formatted)
//                    }
//                }
//
//                if (addToUndoStack) {
//                    CommandProcessor.getInstance().executeCommand(project, writeAction, "Run elm-review on current file", null, document)
//                } else {
//                    CommandProcessor.getInstance().runUndoTransparentAction(writeAction)
//                }
//            }
//        }
//        try {
//            elmReview.review(ctx.project, ctx.document, ctx.elmVersion, addToUndoStack = true)
//        } catch (ex: ExecutionException) {
//            if (isUnitTestMode) throw ex
//            val message = ex.message ?: "something went wrong running elm-format"
//            ctx.project.showBalloon(message, NotificationType.ERROR, fixAction)
//        }
    }

    private fun getContext(e: AnActionEvent): Context? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        if (!file.isInLocalFileSystem) return null
        if (!file.isElmFile) return null
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
        val elmVersion = ElmReviewCLI.getElmVersion(project, file) ?: return null
        return Context(project, file, document, elmVersion)
    }

    data class Context(
            val project: Project,
            val file: VirtualFile,
            val document: Document,
            val elmVersion: Version
    )

    interface ElmErrorsListener {
        fun update(baseDirPath: Path, messages: List<ElmError>, targetPath: String?, offset: Int)
    }

    companion object {
        const val ID = "Elm.RunExternalElmReview" // must stay in-sync with `plugin.xml`
        val ERRORS_TOPIC = Topic("Elm compiler-messages", ElmBuildAction.ElmErrorsListener::class.java)
        // TODO Jeroen
        // val ERRORS_TOPIC = Topic("elm-review errors", ElmBuildAction.ElmErrorsListener::class.java)
    }
}
