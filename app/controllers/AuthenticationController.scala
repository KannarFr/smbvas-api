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
import models.token_module._

@Singleton
class AuthenticatedAction @Inject()(
  env: Environment,
  parser: BodyParsers.Default,
  implicit val ec: ExecutionContext,
  tokenDAO: TokenDAO
) extends ActionBuilderImpl(parser) {
  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
    if (env.mode == Mode.Dev) {
      block(request)
    } else {
      (for {
        token <- request.headers.get("X-Token")
      } yield {
        tokenDAO.getTokenByValue(UUID.fromString(token)).map { token =>
          if (token.expireDate.isAfter(ZonedDateTime.now)) {
            block(request)
          } else {
            Future(BadRequest("Token expired"))
          }
        }.getOrElse(Future(BadRequest("Token does not exist")))
      }).getOrElse(Future(BadRequest("Missing token")))
    }
  }
}
