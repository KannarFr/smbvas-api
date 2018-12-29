package controllers

import javax.inject._
import java.time.ZonedDateTime
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json._

import models.user_module._
import models.AuthorizationChecker

@Singleton
class AuthenticatedAction @Inject()(
  env: Environment,
  parser: BodyParsers.Default,
  implicit val ec: ExecutionContext,
  authChecker: AuthorizationChecker
) extends ActionBuilderImpl(parser) {
  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
    if (env.mode == Mode.Dev) {
      block(request)
    } else {
      (for {
        uuid <- request.headers.get("X-SMBVAS-UserId")
        timestamp <- request.headers.get("X-SMBVAS-Timestamp")
        signature <- request.headers.get("X-SMBVAS-Signature")
      } yield {
        authChecker.check(
          request.method,
          request.uri,
          UUID.fromString(uuid),
          ZonedDateTime.parse(timestamp),
          signature
        ).flatMap(res => {
          if (res.status == 200) {
            block(request)
          } else {
            Future(Unauthorized(res.body))
          }
        }).fallbackTo(Future(InternalServerError))
      }).getOrElse(Future(Unauthorized))
    }
  }
}
