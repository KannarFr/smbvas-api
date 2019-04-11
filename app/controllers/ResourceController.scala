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

@Singleton
class ResourceController @Inject()(
  cellarDTO: CellarDTO,
  cc: ControllerComponents,
  implicit val ec: ExecutionContext,
  resourceDAO: ResourceDAO,
  resourceDTO: ResourceDTO,
  authenticatedAction: AuthenticatedAction
) extends AbstractController(cc) {
  def getResources = authenticatedAction.async { implicit request: Request[AnyContent] =>
    Future(Ok(Json.toJson(resourceDAO.getResources)))
  }

  def getValidatedResources = Action.async { implicit request: Request[AnyContent] =>
    Future(Ok(Json.toJson(resourceDAO.getValidatedResources)))
  }

  def getResourceById(resourceId: UUID) = Action.async { implicit request =>
    Future(Ok(Json.toJson(resourceDAO.getResourceById(resourceId))))
  }

  /*def getResourceFileUrlById(resourceId: UUID) = authenticatedAction.async { implicit request =>
    Future {
      cellarDTO.getResourceUrl(resourceId) match {
        case Right(url) => Ok(Json.toJson(url))
        case Left(_) => NotFound
      }
    }
  }*/

  def patchResourceById(resourceId: UUID) = authenticatedAction.async(parse.json[Resource]) { implicit request =>
    Future {
      val resource = request.body
      resourceDAO.patchResource(resource) match {
        case Left(_) => InternalServerError
        case Right(_) => NoContent
      }
    }
  }

  def create = Action.async(parse.json[WannabeResource]) { implicit request =>
    Future {
      val resource = request.body
      resourceDAO.create(resource) match {
        case Left(ResourceAlreadyPresent) => Status(409)
        case Left(_) => InternalServerError
        case Right(resource) => Created(Json.toJson(resource))
      }
    }
  }

  def uploadResourceContentToResourceId(resourceId: UUID) = Action.async(parse.multipartFormData) { implicit request =>
    Future {
      request.body.file("resource") match {
        case Some(FilePart(key, filename, contentType, ref)) => {
          resourceDTO.processFile(resourceId, key, filename, contentType, ref) match {
            case Left(e) => InternalServerError(e.toString)
            case Right(resource) => Created
          }
        }
        case _ => InternalServerError("Can't find file in request.")
      }
    }
  }
}
