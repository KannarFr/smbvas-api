package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID
import models.user_module._
import models.user_module.User._

@Singleton
class UserController @Inject()(
  cc: ControllerComponents,
  userDAO: UserDAO,
  authenticatedAction: AuthenticatedAction
) extends AbstractController(cc) {
  def getUsers = authenticatedAction.async { implicit request: Request[AnyContent] =>
    Future {
      Ok(Json.toJson(userDAO.getUsers))
    }
  }

  def getUserById(uuid: String) = authenticatedAction.async { implicit request =>
    Future {
      Ok(Json.toJson(userDAO.getUserById(UUID.fromString(uuid))))
    }
  }

  def patchUserById(uuid: String) = authenticatedAction.async(parse.json[User]) { implicit request =>
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
