package com.karumi.shot.screenshots

import java.io.File

import com.karumi.shot.domain._
import com.karumi.shot.domain.model.ScreenshotsSuite
import com.sksamuel.scrimage.ImmutableImage

class ScreenshotsComparator {

  def compare(screenshots: ScreenshotsSuite): ScreenshotsComparisionResult = {
    val errors = screenshots.par.flatMap(compareScreenshot).toList
    ScreenshotsComparisionResult(errors, screenshots)
  }

  private def compareScreenshot(
      screenshot: Screenshot): Option[ScreenshotComparisionError] = {
    val recordedScreenshotFile = new File(screenshot.recordedScreenshotPath)
    if (!recordedScreenshotFile.exists()) {
      Some(ScreenshotNotFound(screenshot))
    } else {
      val oldScreenshot =
        ImmutableImage.loader().fromFile(recordedScreenshotFile)
      val newScreenshot = ScreenshotComposer.composeNewScreenshot(screenshot)
      if (!haveSameDimensions(newScreenshot, oldScreenshot)) {
        val originalDimension =
          Dimension(oldScreenshot.width, oldScreenshot.height)
        val newDimension = Dimension(newScreenshot.width, newScreenshot.height)
        Some(
          DifferentImageDimensions(screenshot,
                                   originalDimension,
                                   newDimension))
      } else if (newScreenshot != oldScreenshot) {
        Some(DifferentScreenshots(screenshot))
      } else {
        None
      }
    }
  }

  private def haveSameDimensions(newScreenshot: ImmutableImage,
                                 recordedScreenshot: ImmutableImage): Boolean =
    newScreenshot.width == recordedScreenshot.width && newScreenshot.height == recordedScreenshot.height

}
