package com.karumi.shot.base64

import java.io.{ByteArrayOutputStream, File}
import java.nio.charset.StandardCharsets
import java.util.Base64

import javax.imageio.ImageIO

class Base64Encoder {

  def base64FromFile(filePath: String): Option[String] = {
    var outputStream: ByteArrayOutputStream = null
    try {
      val diffScreenshotFile = new File(filePath)
      val bufferedImage = ImageIO.read(diffScreenshotFile)
      outputStream = new ByteArrayOutputStream()
      ImageIO.write(bufferedImage, "png", outputStream)
      val diffImageBase64Encoded =
        Base64.getEncoder.encode(outputStream.toByteArray)
      val diffBase64UTF8 = new String(diffImageBase64Encoded, StandardCharsets.UTF_8)
      Some(diffBase64UTF8)
    } catch {
      case _: Exception => None
    } finally {
      if (outputStream != null) {
        outputStream.close()
      }
    }
  }
}
