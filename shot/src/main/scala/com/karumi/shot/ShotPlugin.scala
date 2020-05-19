package com.karumi.shot

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.{AppExtension, LibraryExtension}
import com.android.builder.model.{BuildType, ProductFlavor}
import com.karumi.shot.android.Adb
import com.karumi.shot.base64.Base64Encoder
import com.karumi.shot.domain.Config
import com.karumi.shot.exceptions.ShotException
import com.karumi.shot.reports.{ConsoleReporter, ExecutionReporter}
import com.karumi.shot.screenshots.{ScreenshotsComparator, ScreenshotsDiffGenerator, ScreenshotsSaver}
import com.karumi.shot.tasks._
import com.karumi.shot.ui.Console
import com.sksamuel.scrimage.nio.PngWriter
import org.gradle.api.artifacts.{Dependency, DependencyResolutionListener, ResolvableDependencies}
import org.gradle.api.{Plugin, Project, Task}

class ShotPlugin extends Plugin[Project] {

  private val console = new Console
  private lazy val shot: Shot =
    new Shot(
      new Adb,
      new Files,
      new ScreenshotsComparator,
      new ScreenshotsDiffGenerator(new Base64Encoder, new PngWriter()),
      new ScreenshotsSaver,
      console,
      new ExecutionReporter,
      new ConsoleReporter(console)
    )

  override def apply(project: Project): Unit = {
    addExtensions(project)
    addAndroidTestDependency(project)
    project.afterEvaluate { project =>
      {
        configureAdb(project)
        addTasks(project)
      }
    }
  }

  private def configureAdb(project: Project): Unit = {
    val adbPath = AdbPathExtractor.extractPath(project)
    shot.configureAdbPath(adbPath)
  }

  private def addTasks(project: Project): Unit = {
    if (isAnAndroidProject(project)) {
      addTasksToAppModule(project)
    } else if (isAnAndroidLibrary(project)) {
      addTasksToLibraryModule(project)
    }
  }

  private def addTasksToLibraryModule(project: Project) = {
    val libraryExtension =
      getAndroidLibraryExtension(project)
    val baseTask = project.getTasks.create(
      Config.defaultTaskName,
      classOf[ExecuteScreenshotTestsForEveryFlavor])
    libraryExtension.getLibraryVariants.all { variant =>
      addTaskToVariant(project, baseTask, variant)
    }
  }

  private def addTasksToAppModule(project: Project) = {
    val appExtension =
      getAndroidAppExtension(project)
    val baseTask = project.getTasks.create(
      Config.defaultTaskName,
      classOf[ExecuteScreenshotTestsForEveryFlavor])
    appExtension.getApplicationVariants.all { variant =>
      addTaskToVariant(project, baseTask, variant)
    }
  }

  private def addTaskToVariant(project: Project,
                               baseTask: ExecuteScreenshotTestsForEveryFlavor,
                               variant: BaseVariant) = {
    val flavor = variant.getMergedFlavor
    checkIfApplicationIdIsConfigured(project, flavor)
    val completeAppId = flavor.getApplicationId + Option(
      flavor.getApplicationIdSuffix).getOrElse("") +
      Option(variant.getBuildType.getApplicationIdSuffix).getOrElse("") +
      ".test"
    val appTestId =
      Option(flavor.getTestApplicationId).getOrElse(completeAppId)
    if (variant.getBuildType.getName != "release") {
      addTasksFor(project,
                  variant.getFlavorName,
                  variant.getBuildType,
                  appTestId,
                  baseTask)
    }
  }

  private def checkIfApplicationIdIsConfigured(project: Project,
                                               flavor: ProductFlavor) =
    if (isAnAndroidLibrary(project) && flavor.getTestApplicationId == null) {
      throw ShotException(
        "Your Android library needs to be configured using an testApplicationId in your build.gradle defaultConfig block.")
    }

  private def addExtensions(project: Project): Unit = {
    val name = ShotExtension.name
    project.getExtensions.add(name, new ShotExtension())
  }

  private def addTasksFor(project: Project,
                          flavor: String,
                          buildType: BuildType,
                          appId: String,
                          baseTask: Task): Unit = {
    val extension =
      project.getExtensions.getByType[ShotExtension](classOf[ShotExtension])
    val instrumentationTask = if (extension.useComposer) {
      Config.composerInstrumentationTestTask(flavor, buildType.getName)
    } else {
      Config.defaultInstrumentationTestTask(flavor, buildType.getName)
    }
    val tasks = project.getTasks
    val removeScreenshots = tasks
      .create(RemoveScreenshotsTask.name(flavor, buildType),
              classOf[RemoveScreenshotsTask])
      .asInstanceOf[ShotTask]
    removeScreenshots.setDescription(
      RemoveScreenshotsTask.description(flavor, buildType))
    removeScreenshots.flavor = flavor
    removeScreenshots.buildType = buildType
    removeScreenshots.appId = appId
    val downloadScreenshots = tasks
      .create(DownloadScreenshotsTask.name(flavor, buildType),
              classOf[DownloadScreenshotsTask])
    downloadScreenshots.setDescription(
      DownloadScreenshotsTask.description(flavor, buildType))
    downloadScreenshots.flavor = flavor
    downloadScreenshots.buildType = buildType
    downloadScreenshots.appId = appId
    val executeScreenshot = tasks
      .create(ExecuteScreenshotTests.name(flavor, buildType),
              classOf[ExecuteScreenshotTests])
    executeScreenshot.setDescription(
      ExecuteScreenshotTests.description(flavor, buildType))
    executeScreenshot.flavor = flavor
    executeScreenshot.buildType = buildType
    executeScreenshot.appId = appId
    if (extension.runInstrumentation) {
      executeScreenshot.dependsOn(instrumentationTask)
      executeScreenshot.dependsOn(downloadScreenshots)
      executeScreenshot.dependsOn(removeScreenshots)
      downloadScreenshots.mustRunAfter(instrumentationTask)
      removeScreenshots.mustRunAfter(downloadScreenshots)
    }
    baseTask.dependsOn(executeScreenshot)
  }

  private def addAndroidTestDependency(project: Project): Unit = {

    project.getGradle.addListener(new DependencyResolutionListener() {

      override def beforeResolve(
          resolvableDependencies: ResolvableDependencies): Unit = {
        var shotAndroidDependencyHasBeenAdded = false

        project.getConfigurations.forEach(config => {
          shotAndroidDependencyHasBeenAdded |= config.getAllDependencies
            .toArray(new Array[Dependency](0))
            .exists(dependency =>
              Config.androidDependencyGroup == dependency.getGroup
                && Config.androidDependencyName == dependency.getName)
        })

        if (!shotAndroidDependencyHasBeenAdded) {
          val dependencyMode = Config.androidDependencyMode
          val dependencyName = Config.androidDependency
          val dependenciesHandler = project.getDependencies

          val dependencyToAdd = dependenciesHandler.create(dependencyName)
          project.getDependencies.add(dependencyMode, dependencyToAdd)
          project.getGradle.removeListener(this)
        }
      }

      override def afterResolve(
          resolvableDependencies: ResolvableDependencies): Unit = {}
    })
  }

  private def isAnAndroidLibrary(project: Project): Boolean =
    try {
      getAndroidLibraryExtension(project)
      true
    } catch {
      case _: Throwable => false
    }

  private def isAnAndroidProject(project: Project): Boolean =
    try {
      getAndroidAppExtension(project)
      true
    } catch {
      case _: Throwable => false
    }

  private def getAndroidLibraryExtension(project: Project) = {
    project.getExtensions
      .getByType[LibraryExtension](classOf[LibraryExtension])
  }

  private def getAndroidAppExtension(project: Project) = {
    project.getExtensions.getByType[AppExtension](classOf[AppExtension])
  }
}
