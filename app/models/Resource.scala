package models

import javax.inject._
import play.api.db._
import anorm._
import anorm.SqlParser._
import models.pg.pg_entity_module._
import models.pg.AnormType._
import play.api.libs.json._
import java.util.UUID
import scala.util.{Try, Success, Failure}
import play.api.Logger
import play.api.libs.Files._
import java.nio.file._
import java.io._
import java.time.ZonedDateTime
import scala.util.matching.Regex

import models.user_module.User
import models.cellar_module._
import utils.Configuration

object resource_module {
  case class WannabeResource(
    label: String,
    description: Option[String],
    date: Option[ZonedDateTime],
    lat: Option[Double],
    lng: Option[Double],
    providerContact: String,
    providerFirstName: String,
    providerLastName: String
  )

  case class ResourcePublicView(
    id: UUID,
    `type`: Option[String] = None,
    label: String,
    description: Option[String],
    color: Option[String] = None,
    lat: Option[Double],
    lng: Option[Double],
    date: Option[ZonedDateTime],
    url: Option[String] = None
  )

  case class Resource(
    id: UUID,
    `type`: Option[String] = None,
    label: String,
    description: Option[String],
    color: Option[String] = None,
    lat: Option[Double],
    lng: Option[Double],
    status: String,
    date: Option[ZonedDateTime],
    providerContact: String,
    providerFirstName: String,
    providerLastName: String,
    url: Option[String] = None,
    size: Option[Double] = None,
    creationDate: ZonedDateTime,
    deletionDate: Option[ZonedDateTime] = None,
    editionDate: Option[ZonedDateTime] = None,
    validator: Option[UUID] = None
  )

  sealed abstract class ResourceError
  case object ResourceAlreadyPresent extends ResourceError
  case object ResourceDoesNotExists extends ResourceError
  case object ResourceFileCantBeStored extends ResourceError
  case object ResourceHasntContentType extends ResourceError
  case object ResourceContentTypeIsNotValid extends ResourceError
  case object ResourceDAOFailure extends ResourceError
  case object UnhandledException extends ResourceError

  implicit val wannabeResourceFormat = Json.format[WannabeResource]
  implicit val resourcePublicViewFormat = Json.format[ResourcePublicView]
  implicit val resourceFormat = Json.format[Resource]

  object Resource {
    implicit val resourcePgEntity: PgEntity[Resource] = new PgEntity[Resource] {
      val tableName = "resource"

      val columns = List(
        PgField("id", Some("uuid"), true),
        PgField("type"),
        PgField("label"),
        PgField("description"),
        PgField("color"),
        PgField("lat"),
        PgField("lng"),
        PgField("status"),
        PgField("date"),
        PgField("provider_contact"),
        PgField("provider_firstname"),
        PgField("provider_lastname"),
        PgField("url"),
        PgField("size"),
        PgField("creation_date"),
        PgField("deletion_date"),
        PgField("edition_date"),
        PgField("validator", Some("uuid"))
      )

      def parser(prefix: String): RowParser[Resource] = {
        get[UUID]("id") ~
        get[Option[String]]("type") ~
        get[String]("label") ~
        get[Option[String]]("description") ~
        get[Option[String]]("color") ~
        get[Option[Double]]("lat") ~
        get[Option[Double]]("lng") ~
        get[String]("status") ~
        get[Option[ZonedDateTime]]("date") ~
        get[String]("provider_contact") ~
        get[String]("provider_firstname") ~
        get[String]("provider_lastname") ~
        get[Option[String]]("url") ~
        get[Option[Double]]("size") ~
        get[ZonedDateTime]("creation_date") ~
        get[Option[ZonedDateTime]]("deletion_date") ~
        get[Option[ZonedDateTime]]("edition_date") ~
        get[Option[UUID]]("validator") map {
          case (
            id ~ typ ~ label ~ description ~ color ~ lat ~ lng ~ status ~
            date ~ providerContact ~ providerFirstName ~ providerLastName ~
            url ~ size ~ creation_date ~ deletion_date ~ edition_date ~
            validator
          ) => Resource(
            id, typ, label, description, color, lat, lng, status,
            date, providerContact, providerFirstName, providerLastName,
            url, size, creation_date, deletion_date, edition_date,
            validator
          )
        }
      }
    }
  }

  class ResourceDTO @Inject()(
    resourceDAO: ResourceDAO,
    cellarDTO: CellarDTO,
    configuration: Configuration
  ) {
    import Resource._

    def processFile(
      id: UUID, key: String, filename: String, contentType: Option[String], ref: TemporaryFile
    ): Either[ResourceError, Unit] = {
      checkContentType(contentType) match {
        case Left(e) => Left(e)
        case Right(contentType) => {
          cellarDTO.createResourceFile(id, ref) match {
            case Left(e) => {
              println(e)
              Left(ResourceFileCantBeStored)
            }
            case Right(_) => {
              resourceDAO.getResourceById(id) match {
                case Some(resource) => {
                  val newResource = resource.copy(
                    `type` = Some(contentType),
                    editionDate = Some(ZonedDateTime.now),
                    status = "FILE_UPDATED", // TODO: TO BE FIXED BY ENUM
                    url = cellarDTO.getResourceUrl(resource.id).toOption.map(_.toString)
                  )
                  resourceDAO.patchResource(newResource)
                }
                case None => Left(ResourceDoesNotExists)
              }
            }
          }
        }
      }
    }

    def checkContentType(contentType: Option[String]): Either[ResourceError, String] = {
      contentType match {
        case None => Left(ResourceHasntContentType)
        case Some(contentType) => {
          "(image|(video)/.*)".r.findFirstMatchIn(contentType) match {
            case Some(contentType) => Right(contentType.toString)
            case None => {
              Logger.error("contentType: ${contentType} is not valid")
              Left(ResourceContentTypeIsNotValid)
            }
          }
        }
      }
    }
  }

  class ResourceDAO @Inject()(db: Database) {
    import Resource._

    def getResources: List[Resource] = db.withConnection { implicit c =>
      SQL(selectSQL[Resource])
        .as(parser[Resource]().*)
    }

    def getValidatedResources: List[List[ResourcePublicView]] = db.withConnection { implicit c =>
      SQL(selectSQL[Resource] + " WHERE status = {status}")
        .on('status -> "VALIDATED")
        .as(parser[Resource]().*)
        .map { resource =>
          ResourcePublicView(
            id = resource.id,
            `type` = resource.`type`,
            label = resource.label,
            description = resource.description,
            color = resource.color,
            lat = resource.lat,
            lng = resource.lng,
            date = resource.date,
            url = resource.url.map(_.replace("http://", "https://"))
          )
        }
        .groupBy(e => (e.lat, e.lng))
        .map { case ((_,_), e) => e }
        .toList
    }

    def getResourceById(id: UUID): Option[Resource] = db.withConnection { implicit c =>
      SQL(selectSQL[Resource] + " WHERE id = {id}::uuid")
        .on('id -> id)
        .as(parser[Resource]().singleOpt)
    }

    def validateResourceById(id: UUID): Either[ResourceError, Unit] = db.withConnection { implicit c =>
      Try {
        SQL(updateSQL[Resource](List("creation_date","type","label","description","color","lat","lng","date","provider_contact","provider_firstname","provider_lastname","url","size","deletion_date","edition_date","validator")))
          .on(
            'id -> id,
            'status -> "VALIDATED"
          )
          .executeUpdate
        ()
      } match {
        case Failure(e) => {
          Logger.error("Something wrong happened while patching a resource")
          e.printStackTrace
          Left(UnhandledException)
        }
        case Success(s) => Right(s)
      }
    }

    def unvalidateResourceById(id: UUID): Either[ResourceError, Unit] = db.withConnection { implicit c =>
      Try {
        SQL(updateSQL[Resource](List("creation_date","type","label","description","color","lat","lng","date","provider_contact","provider_firstname","provider_lastname","url","size","deletion_date","edition_date","validator")))
          .on(
            'id -> id,
            'status -> "UNVALIDATED"
          )
          .executeUpdate
        ()
      } match {
        case Failure(e) => {
          Logger.error("Something wrong happened while patching a resource")
          e.printStackTrace
          Left(UnhandledException)
        }
        case Success(s) => Right(s)
      }
    }

    def patchResource(resource: Resource): Either[ResourceError, Unit] = db.withConnection { implicit c =>
      Try {
        SQL(updateSQL[Resource](List("creation_date")))
          .on(
            'id -> resource.id,
            'type -> resource.`type`,
            'label -> resource.label,
            'description -> resource.description,
            'color -> resource.color,
            'lat -> resource.lat,
            'lng -> resource.lng,
            'status -> resource.status,
            'date -> resource.date,
            'provider_contact -> resource.providerContact,
            'provider_firstname -> resource.providerFirstName,
            'provider_lastname -> resource.providerLastName,
            'url -> resource.url,
            'size -> resource.size,
            'deletion_date -> resource.deletionDate,
            'edition_date -> resource.editionDate,
            'validator -> resource.validator
          )
          .executeUpdate
        ()
      } match {
        case Failure(e) => {
          Logger.error("Something wrong happened while patching a resource")
          e.printStackTrace
          Left(UnhandledException)
        }
        case Success(s) => Right(s)
      }
    }

    def create(wannabeResource: WannabeResource): Either[ResourceError, Resource] = db.withConnection { implicit c =>
      val resource = Resource(
        id = UUID.randomUUID,
        label = wannabeResource.label,
        description = wannabeResource.description,
        lat = wannabeResource.lat,
        lng = wannabeResource.lng,
        status = "EMPTY", // TODO: TO BE FIXED BY ENUM
        date = wannabeResource.date,
        providerContact = wannabeResource.providerContact,
        providerFirstName = wannabeResource.providerFirstName,
        providerLastName = wannabeResource.providerLastName,
        creationDate = ZonedDateTime.now
      )

      Try {
        SQL(insertSQL[Resource])
          .on(
            'id -> resource.id,
            'type -> resource.`type`,
            'label -> resource.label,
            'description -> resource.description,
            'color -> resource.color,
            'lat -> resource.lat,
            'lng -> resource.lng,
            'status -> resource.status,
            'date -> resource.date,
            'provider_contact -> resource.providerContact,
            'provider_firstname -> resource.providerFirstName,
            'provider_lastname -> resource.providerLastName,
            'url -> resource.url,
            'size -> resource.size,
            'creation_date -> resource.creationDate,
            'deletion_date -> resource.deletionDate,
            'edition_date -> resource.editionDate,
            'validator -> resource.validator
          ).execute
        resource
      } match {
        case Failure(e: org.postgresql.util.PSQLException) => {
          if(e.getSQLState == "23505") {
            Left(ResourceAlreadyPresent)
          } else {
            Logger.error("Something wrong happened while inserting a Resource")
            e.printStackTrace
            Left(UnhandledException)
          }
        }
        case Failure(e) => {
          Logger.error("Something wrong happened while inserting a Resource")
          e.printStackTrace
          Left(UnhandledException)
        }
        case Success(s) => Right(s)
      }
    }
  }
}
