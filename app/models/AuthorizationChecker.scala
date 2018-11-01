package models

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject._
import java.time.ZonedDateTime
import java.util.UUID

import scala.concurrent.Future

import play.api.libs.json.Json
import play.api.libs.ws._
import play.api.Logger

import utils.Configuration

class AuthorizationChecker @Inject()(
  conf: Configuration,
  ws: WSClient
) {
  def check(
    method: String,
    url: String,
    uuid: UUID,
    timestamp: ZonedDateTime,
    signature: String
  ): Future[WSResponse] = {
    ws
      .url(s"${conf.authProvider.url}/check")
      .post(Json.obj(
        "method" -> method,
        "url" -> url,
        "userId" -> uuid.toString,
        "date" -> timestamp.toString,
        "signature" -> signature
      ))
  }
}
