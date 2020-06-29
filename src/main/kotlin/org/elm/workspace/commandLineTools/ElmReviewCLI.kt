package org.elm.workspace.commandLineTools

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.elm.lang.core.psi.ElmFile
import org.elm.openapiext.GeneralCommandLine
import org.elm.openapiext.Result
import org.elm.openapiext.execute
import org.elm.openapiext.isSuccess
import org.elm.workspace.ElmApplicationProject
import org.elm.workspace.ElmPackageProject
import org.elm.workspace.ParseException
import org.elm.workspace.Version
import java.nio.file.Path

private val log = logger<ElmReviewCLI>()


/**
 * Interact with external `elm-review` process.
 */
class ElmReviewCLI(private val elmReviewExecutablePath: Path) {

    private fun getReviewedContentOfDocument(elmVersion: Version, document: Document): ProcessOutput {
        val arguments = listOf(
                "--yes",
                "--elm-version=${elmVersion.x}.${elmVersion.y}",
                "--stdin"
        )

        return GeneralCommandLine(elmReviewExecutablePath)
                .withParameters(arguments)
                .execute(document.text)
    }


    fun review(project: Project, document: Document, version: Version, addToUndoStack: Boolean) {

        val result = ProgressManager.getInstance().runProcessWithProgressSynchronously<ProcessOutput, ExecutionException>({
            getReviewedContentOfDocument(version, document)
        }, "Running elm-review on current file...", true, project)

        if (result.isSuccess) {
            val formatted = result.stdout
            val source = document.text

            if (source != formatted) {

                val writeAction = {
                    ApplicationManager.getApplication().runWriteAction {
                        document.setText(formatted)
                    }
                }

                if (addToUndoStack) {
                    CommandProcessor.getInstance().executeCommand(project, writeAction, "Run elm-review on current file", null, document)
                } else {
                    CommandProcessor.getInstance().runUndoTransparentAction(writeAction)
                }
            }
        }
    }


    fun queryVersion(): Result<Version> {
        // Output of `elm-review` is multiple lines where the first line is something like 'elm-review 0.8.1'.
        // NOTE: `elm-review` does not currently support a `--version` argument, so this is going to be brittle.
        val firstLine = try {
            val arguments: List<String> = listOf("--version")
            GeneralCommandLine(elmReviewExecutablePath)
                    .withParameters(arguments)
                    .execute(timeoutInMilliseconds = 3000)
                    .stdoutLines
                    .firstOrNull()
        } catch (e: ExecutionException) {
            return Result.Err("failed to run elm-review: ${e.message}")
        } ?: return Result.Err("no output from elm-review")

        return try {
            Result.Ok(Version.parse(firstLine))
        } catch (e: ParseException) {
            Result.Err("invalid elm-review version: ${e.message}")
        }
    }

    companion object {
        fun getElmVersion(project: Project, file: VirtualFile): Version? {
            val psiFile = ElmFile.fromVirtualFile(file, project) ?: return null

            return when (val elmProject = psiFile.elmProject) {
                is ElmApplicationProject -> elmProject.elmVersion
                is ElmPackageProject -> elmProject.elmVersion.low
                else -> return null
            }
        }
    }
}