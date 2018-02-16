package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import models.user_module._
import models.AuthorizationChecker

@Singleton
class AuthenticatedAction @Inject()(
    env: Environment,
    parser: BodyParsers.Default,
    ec: ExecutionContext
) extends ActionBuilderImpl(parser) {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
        implicit val context = ec
        if (env.mode == Mode.Dev) {
            block(request)
        } else {
            (for {
                application <- request.headers.get("X-API-Application")
                token <- request.headers.get("X-API-Token")
                timestamp <- request.headers.get("X-API-Timestamp")
                signature <- request.headers.get("X-API-Signature")
            } yield {
                if (AuthorizationChecker.check(
                    application,
                    token,
                    timestamp,
                    signature
                )) {
                    Logger.info(s"Access granted to ${application}-${timestamp} using ${token}")
                    block(request)
                } else {
                    Future(Forbidden)(ec)
                }
            }).getOrElse(Future(Unauthorized)(ec))
        }
    }
}