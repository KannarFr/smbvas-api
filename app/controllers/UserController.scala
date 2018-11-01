package controllers

import java.util.UUID
import javax.inject._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import play.api._
import play.api.mvc._
import play.api.libs.json._

import models.user_module._
import models.user_module.User._

@Singleton
class UserController @Inject()(
  authenticatedAction: AuthenticatedAction,
  cc: ControllerComponents,
  implicit val executionContext: ExecutionContext,
  userDAO: UserDAO
) extends AbstractController(cc) {
  def getUsers = authenticatedAction.async { implicit request: Request[AnyContent] =>
    Future(Ok(Json.toJson(userDAO.getUsers)))
  }

  def getUserById(userId: UUID) = authenticatedAction.async { implicit request =>
    Future(Ok(Json.toJson(userDAO.getUserById(userId))))
  }

  def patchUserById(userId: UUID) = authenticatedAction.async(parse.json[User]) { implicit request =>
    Future {
      val user = request.body
      userDAO.patchUser(user) match {
        case Left(_) => InternalServerError
        case Right(_) => NoContent
      }
    }
  }

  def create = authenticatedAction.async(parse.json[User]) { implicit request =>
    Future {
      val user = request.body
      userDAO.create(user) match {
        case Left(UserAlreadyPresent) => Status(409)
        case Left(_) => InternalServerError
        case Right(_) => Created
      }
    }
  }
}
