package controllers

import java.util.UUID
import javax.inject._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Success, Failure}

import play.api._
import play.api.mvc._
import play.api.mvc.MultipartFormData.FilePart
import play.api.libs.json._

import models.cellar_module._
import models.resource_module._
import models.resource_module.Resource._

@Singleton
class ResourceController @Inject()(
  cellarDTO: CellarDTO,
  cc: ControllerComponents,
  implicit val ec: ExecutionContext,
  resourceDAO: ResourceDAO,
  resourceDTO: ResourceDTO,
  authenticatedAction: AuthenticatedAction
) extends AbstractController(cc) {
  def getResources = Action.async { implicit request: Request[AnyContent] =>
    Future {
      Ok(Json.toJson(resourceDAO.getResources))
    }
  }

  def getResourceById(uuid: String) = authenticatedAction.async { implicit request =>
    Future {
      cellarDTO.getResourceUrl(UUID.fromString(uuid)) match {
        case Right(url) => {
          Ok(Json.toJson(resourceDAO.getResourceById(UUID.fromString(uuid))))
        }
        case Left(_) => {
          BadRequest
        }
      }
    }
  }

  def patchResourceById(uuid: String) = authenticatedAction.async(parse.json[Resource]) { implicit request =>
    Future {
      val resource = request.body
      resourceDAO.patchResource(resource) match {
        case Left(_) => InternalServerError
        case Right(_) => NoContent
      }
    }
  }

  def create = authenticatedAction.async(parse.json[Resource]) { implicit request =>
    Future {
      val resource = request.body
      resourceDAO.create(resource) match {
        case Left(ResourceAlreadyPresent) => Status(409)
        case Left(_) => InternalServerError
        case Right(_) => Created
      }
    }
  }

  def uploadResourceContentToResourceId(uuid: String) = authenticatedAction.async(parse.multipartFormData) { implicit request =>
    Future {
      request.body.file("resource") match {
        case Some(FilePart(key, filename, contentType, ref)) => {
          resourceDTO.processFile(UUID.fromString(uuid), key, filename, contentType, ref) match {
            case Left(_) => InternalServerError
            case Right(resource) => Created
          }
        }
        case _ => InternalServerError
      }
    }
  }
}
