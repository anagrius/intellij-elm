package org.elm.ide.actions

import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.messages.Topic
import org.elm.ide.notifications.showBalloon
import org.elm.lang.core.ElmFileType
import org.elm.openapiext.isUnitTestMode
import org.elm.workspace.elmToolchain
import org.elm.workspace.elmWorkspace
import org.elm.workspace.elmreview.ElmReviewError
import org.elm.workspace.elmreview.elmReviewJsonToMessages
import java.nio.file.Path


private val log = logger<ElmExternalReviewAction>()

class ElmExternalReviewAction : AnAction() {

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = e.project != null
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

        val fixAction = "Fix" to { project.elmWorkspace.showConfigureToolchainUI() }

        val elmReview = project.elmToolchain.elmReviewCLI

        if (elmReview == null) {
            project.showBalloon("Could not find elm-review", NotificationType.ERROR, fixAction)
            return
        }

        val json = try {
            elmReviewCLI.runReview(elmProject, project.elmToolchain.elmCLI).stdout
        } catch (e: ExecutionException) {
            showError(project, "execution error $e")
            return
        }
        showError(project, "json $json")

        val messages = if (json.isEmpty()) emptyList() else {
            elmReviewJsonToMessages(json).sortedWith(
                    compareBy(
                            { it.path },
                            { it.region.start.line },
                            { it.region.start.column }
                    ))
        }

        project.messageBus.syncPublisher(ERRORS_TOPIC).update(elmProject.projectDirPath, messages, null, 0)
        if (isUnitTestMode) return
        ToolWindowManager.getInstance(project).getToolWindow("elm-review").show(null)
    }

    interface ElmReviewErrorsListener {
        fun update(baseDirPath: Path, messages: List<ElmReviewError>, targetPath: String?, offset: Int)
    }

    companion object {
        const val ID = "Elm.RunExternalElmReview" // must stay in-sync with `plugin.xml`
        val ERRORS_TOPIC = Topic("elm-review errors", ElmReviewErrorsListener::class.java)
    }
}
