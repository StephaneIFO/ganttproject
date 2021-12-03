/*
Copyright 2019 BarD Software s.r.o

This file is part of GanttProject, an opensource project management tool.

GanttProject is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

GanttProject is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
*/
package net.sourceforge.ganttproject

import biz.ganttproject.app.RootLocalizer
import biz.ganttproject.app.SingleTranslationLocalizer
import biz.ganttproject.app.showAsync
import com.bardsoftware.eclipsito.update.UpdateIntegrityChecker
import com.bardsoftware.eclipsito.update.UpdateMetadata
import com.bardsoftware.eclipsito.update.UpdateProgressMonitor
import com.bardsoftware.eclipsito.update.Updater
import com.beust.jcommander.JCommander
import javafx.application.Platform
import net.sourceforge.ganttproject.export.CommandLineExportApplication
import net.sourceforge.ganttproject.gui.CommandLineProjectOpenStrategy
import net.sourceforge.ganttproject.language.GanttLanguage
import net.sourceforge.ganttproject.plugins.PluginManager
import net.sourceforge.ganttproject.task.TaskManagerImpl
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.io.PrintStream
import java.lang.Thread.UncaughtExceptionHandler
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.SwingUtilities


fun main(args: Array<String>) {
  AppBuilder(args).withLogging().withWindowVisible().runBeforeUi {
    RootLocalizer = SingleTranslationLocalizer(ResourceBundle.getBundle("i18n"))
    PluginManager.setCharts(listOf())
    GanttLanguage.getInstance()
  }.whenAppInitialized {
    // This is a dummy updater just to make gradle run working
    val updater = object : Updater {
      override fun getUpdateMetadata(p0: String?) = CompletableFuture.completedFuture(listOf<UpdateMetadata>())
      override fun installUpdate(p0: UpdateMetadata?, p1: UpdateProgressMonitor?, p2: UpdateIntegrityChecker?): CompletableFuture<File> {
        TODO("Not yet implemented")
      }
    }
    it.updater = updater
  }.launch()

//  val mainArgs = GanttProject.Args()
//  JCommander(arrayOf<Any>(mainArgs), *args)
//  startUiApp(mainArgs) {
//    it.updater = updater
//  }
}

val mainWindow = AtomicReference<GanttProject?>(null)

/**
 * @author dbarashev@bardsoftware.com
 */
@JvmOverloads
fun startUiApp(args: GanttProject.Args, configure: (GanttProject) -> Unit = {}) {

  Platform.setImplicitExit(false)
  SwingUtilities.invokeLater {
    try {
      val ganttFrame = GanttProject(false)
      configure(ganttFrame)
      APP_LOGGER.debug("Main frame created")
      mainWindow.set(ganttFrame)
    } catch (e: Exception) {
      APP_LOGGER.error("Failure when launching application", exception = e)
    } finally {
    }
  }
}

val APP_LOGGER = GPLogger.create("App")

typealias RunBeforeUi = ()->Unit
typealias RunAfterWindowOpened = (JFrame) -> Unit
typealias RunAfterAppInitialized = (GanttProject) -> Unit
typealias RunWhenDocumentReady = (IGanttProject) -> Unit

class AppBuilder(private val args: Array<String>) {
  val mainArgs = GanttProject.Args()
  val cliArgs = CommandLineExportApplication.Args()
  val cliParser = JCommander(arrayOf(mainArgs, cliArgs), *args)

  fun isCli(): Boolean = !cliArgs.exporter.isNullOrBlank()
  private val runBeforeUiCommands = mutableListOf<RunBeforeUi>()
  private val runAfterWindowOpenedCommands = mutableListOf<RunAfterWindowOpened>()
  private val runAfterAppInitializedCommands = mutableListOf<RunAfterAppInitialized>()
  private val runWhenDocumentReady = mutableListOf<RunWhenDocumentReady>()

  fun runBeforeUi(cmd: RunBeforeUi): AppBuilder {
    runBeforeUiCommands.add(cmd)
    return this
  }
  fun withLogging(): AppBuilder {
    runBeforeUi {
      if (mainArgs.log && "auto".equals(mainArgs.logFile)) {
        mainArgs.logFile = System.getProperty("user.home") + File.separator + "ganttproject.log";
      }
      if (mainArgs.log && !mainArgs.logFile.trim().isEmpty()) {
        try {
          GPLogger.setLogFile(mainArgs.logFile);
          File(mainArgs.logFile).also {
            System.setErr(PrintStream(it.outputStream()))
          }
        } catch (ex: Exception) {
          println("Failed to write log to file: " + ex.message);
          ex.printStackTrace();
        }
      }

      GPLogger.logSystemInformation();

    }
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
      GPLogger.log(e)
    }
    SwingUtilities.invokeLater {
      Thread.currentThread().uncaughtExceptionHandler = UncaughtExceptionHandler {
          _, e -> GPLogger.log(e)
      }
    }
    whenWindowOpened {
      Platform.runLater {
        Thread.currentThread().uncaughtExceptionHandler = UncaughtExceptionHandler {
            _, e -> GPLogger.log(e)
        }
      }
    }
    return this
  }
  fun withSplash(): AppBuilder {
    val splashCloser = showAsync().get()
    whenWindowOpened {
      try {
        splashCloser.run()
      } catch (ex: Exception) {
        ex.printStackTrace()
      }
    }
    return this
  }
  fun withWindowVisible(): AppBuilder {
    whenAppInitialized { ganttProject ->
      SwingUtilities.invokeLater { ganttProject.doShow() }
    }
    return this
  }
  fun withDocument(path: String): AppBuilder {
    whenAppInitialized { ganttProject ->
      val strategy = CommandLineProjectOpenStrategy(ganttProject.project, ganttProject.documentManager,
        ganttProject.taskManager as TaskManagerImpl, ganttProject.uiFacade, ganttProject.projectUIFacade,
        ganttProject.ganttOptions.pluginPreferences)
      strategy.openStartupDocument(path, { ganttProject.fireProjectCreated() }) { doc ->
        runWhenDocumentReady.forEach { cmd -> cmd(ganttProject.project) }
      }

    }
    return this
  }

  fun whenAppInitialized(code: RunAfterAppInitialized): AppBuilder {
    runAfterAppInitializedCommands.add(code)
    return this
  }
  fun whenWindowOpened(code: RunAfterWindowOpened): AppBuilder {
    runAfterWindowOpenedCommands.add(code)
    return this
  }
  fun whenDocumentReady(code: RunWhenDocumentReady): AppBuilder {
    runWhenDocumentReady.add(code)
    return this
  }

  fun launch() {
    runBeforeUiCommands.forEach { cmd -> cmd() }
    startUiApp(mainArgs) { ganttProject: GanttProject ->
      ganttProject.updater = org.eclipse.core.runtime.Platform.getUpdater()
      ganttProject.addWindowListener(object : WindowAdapter() {
        override fun windowOpened(e: WindowEvent?) {
          runAfterWindowOpenedCommands.forEach { cmd -> cmd(ganttProject) }
        }
      })
      ganttProject.uiInitializationPromise.await {
        runAfterAppInitializedCommands.forEach { cmd -> cmd(ganttProject) }
      }
    }
  }


}
