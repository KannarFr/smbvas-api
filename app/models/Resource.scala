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
  case class Resource(
    uuid: UUID,
    `type`: Option[String],
    label: Option[String],
    description: Option[String],
    color: Option[String],
    lat: Double,
    lng: Double,
    date: ZonedDateTime,
    url: Option[String],
    size: Option[Double],
    creation_date: ZonedDateTime,
    deletion_date: Option[ZonedDateTime],
    edition_date: Option[ZonedDateTime],
    validator: UUID
  )

  sealed abstract class ResourceError
  case object ResourceAlreadyPresent extends ResourceError
  case object ResourceDoesNotExists extends ResourceError
  case object ResourceFileCantBeStored extends ResourceError
  case object ResourceHasntContentType extends ResourceError
  case object ResourceContentTypeIsNotValid extends ResourceError
  case object ResourceDAOFailure extends ResourceError
  case object UnhandledException extends ResourceError

  object Resource {
    implicit val resourceFormat = Json.format[Resource]
    implicit val resourcePgEntity: PgEntity[Resource] = new PgEntity[Resource] {
      val tableName = "resources"

      val columns = List(
        PgField("uuid"),
        PgField("type"),
        PgField("label"),
        PgField("description"),
        PgField("color"),
        PgField("lat"),
        PgField("lng"),
        PgField("date"),
        PgField("url"),
        PgField("size"),
        PgField("creation_date"),
        PgField("deletion_date"),
        PgField("edition_date"),
        PgField("validator")
      )

      def parser(prefix: String): RowParser[Resource] = {
        get[UUID]("uuid") ~
        get[Option[String]]("type") ~
        get[Option[String]]("label") ~
        get[Option[String]]("description") ~
        get[Option[String]]("color") ~
        get[Double]("lat") ~
        get[Double]("lng") ~
        get[ZonedDateTime]("date") ~
        get[Option[String]]("url") ~
        get[Option[Double]]("size") ~
        get[ZonedDateTime]("creation_date") ~
        get[Option[ZonedDateTime]]("deletion_date") ~
        get[Option[ZonedDateTime]]("edition_date") ~
        get[UUID]("validator") map {
          case (
            uuid ~ typ ~ label ~ description ~ color ~ lat ~ lng ~
            date ~ url ~ size ~ creation_date ~ deletion_date ~ edition_date ~
            validator
          ) => Resource(
            uuid, typ, label, description, color, lat, lng,
            date, url, size, creation_date, deletion_date, edition_date,
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
      uuid: UUID, key: String, filename: String, contentType: Option[String], ref: TemporaryFile
    ): Either[ResourceError, Unit] = {
      checkContentType(contentType) match {
        case Left(e) => Left(e)
        case Right(contentType) => {
          cellarDTO.createResourceFile(uuid, ref) match {
            case Left(e) => Left(ResourceFileCantBeStored)
            case Right(_) => {
              resourceDAO.getResourceById(uuid) match {
                case Some(resource) => {
                  val newResource = resource.copy(
                    `type` = Some(contentType),
                    edition_date = Some(ZonedDateTime.now)
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

    def getResources(): List[Resource] = db.withConnection { implicit c =>
      SQL(s"""
        SELECT r.*, row_to_json(u.*) as validator_js
        FROM users u, resources r
        WHERE r.validator = u.uuid
      """) as (parser[Resource]().*)
    }

    def getResourceById(uuid: UUID): Option[Resource] = db.withConnection { implicit c =>
      SQL(s"""
        SELECT r.*, row_to_json(u.*) as validator_js
        FROM users u, resources r
        WHERE r.validator = u.uuid
        AND r.uuid = {uuid}
      """).on(
        'uuid -> uuid
      ).as(parser[Resource]().singleOpt)
    }

    def patchResource(resource: Resource): Either[ResourceError, Unit] = db.withConnection { implicit c =>
      Try {
        SQL(updateSQL[Resource](List("uuid", "creation_date"))).on(
          'type -> resource.`type`,
          'label -> resource.label,
          'description -> resource.description,
          'color -> resource.color,
          'lat -> resource.lat,
          'lng -> resource.lng,
          'date -> resource.date,
          'url -> resource.url,
          'size -> resource.size,
          'deletion_date -> resource.deletion_date,
          'edition_date -> resource.edition_date,
          'validator -> resource.validator
        ).executeUpdate
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

    def create(resource: Resource): Either[ResourceError, Unit] = db.withConnection { implicit c =>
      Try {
        SQL(insertSQL[Resource]).on(
          'uuid -> resource.uuid,
          'type -> resource.`type`,
          'label -> resource.label,
          'description -> resource.description,
          'color -> resource.color,
          'lat -> resource.lat,
          'lng -> resource.lng,
          'date -> resource.date,
          'url -> resource.url,
          'size -> resource.size,
          'creation_date -> resource.creation_date,
          'deletion_date -> resource.deletion_date,
          'edition_date -> resource.edition_date,
          'validator -> resource.validator
        ).executeUpdate
        ()
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
