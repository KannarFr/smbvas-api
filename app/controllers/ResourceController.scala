package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.mvc.MultipartFormData.FilePart
import play.api.libs.json._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID
import models.resource_module._
import models.resource_module.Resource._

@Singleton
class ResourceController @Inject()(
  cc: ControllerComponents,
  resourceDAO: ResourceDAO,
  resourceDTO: ResourceDTO,
  authenticatedAction: AuthenticatedAction
) extends AbstractController(cc) {
  def getResources = authenticatedAction.async { implicit request: Request[AnyContent] =>
    Future {
      Ok(Json.toJson(resourceDAO.getResources))
    }
  }

  def getResourceById(uuid: String) = authenticatedAction.async { implicit request =>
    Future {
      Ok(Json.toJson(resourceDAO.getResourceById(UUID.fromString(uuid))))
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
