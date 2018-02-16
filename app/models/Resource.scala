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
import models.user_module.User
import play.api.libs.Files._
import java.nio.file._
import java.io._
import java.time.Instant
import scala.util.matching.Regex
import utils.Configuration

object resource_module {
    case class Resource(
        uuid: UUID,
        category: Option[String],
        color: Option[String],
        lat: Double,
        lng: Double,
        path: Option[String],
        creation_date: Int,
        deletion_date: Option[Int],
        edition_date: Option[Int],
        managed_date: Option[Int],
        validator: Option[User]
    )

    sealed abstract class ResourceError
    case object ResourceAlreadyPresent extends ResourceError
    case object ResourceDoesNotExists extends ResourceError
    case object ResourceCantBeMoved extends ResourceError
    case object ResourceHasntContentType extends ResourceError
    case object ResourceContentTypeIsNotValid extends ResourceError
    case object ResourceDAOFailure extends ResourceError
    case object UnhandledException extends ResourceError

    object Resource {
        implicit val resourceFormat = Json.format[Resource]
        implicit val resourcePgEntity: PgEntity[Resource] = new PgEntity[Resource] {
            val tableName = "resources"

            val columns = List(
                PgField("uuid"), PgField("category"), PgField("color"), PgField("lat"),
                PgField("lng"), PgField("path"), PgField("creation_date"), PgField("deletion_date"),
                PgField("edition_date"), PgField("managed_date"), PgField("validator")
            )

            def parser(prefix: String): RowParser[Resource] = {
                get[UUID]("uuid") ~
                get[Option[String]]("category") ~
                get[Option[String]]("color") ~
                get[Double]("lat") ~
                get[Double]("lng") ~
                get[Option[String]]("path") ~
                get[Int]("creation_date") ~
                get[Option[Int]]("deletion_date") ~
                get[Option[Int]]("edition_date") ~
                get[Option[Int]]("managed_date") ~
                get[JsValue]("validator_js") map {
                    case (
                        uuid ~ category ~ color ~ lat ~ lng ~ path ~ creation_date ~
                        deletion_date ~ edition_date ~ managed_date ~ validator_js
                    ) => Resource(
                        uuid, category, color, lat, lng, path, creation_date, deletion_date,
                        edition_date, managed_date, validator = validator_js.asOpt[User]
                    )
                }
            }
        }
    }

    class ResourceDTO @Inject()(resourceDAO: ResourceDAO, configuration: Configuration) {
        import Resource._

        def processFile(
            uuid: UUID, key: String, filename: String, contentType: Option[String], ref: TemporaryFile
        ): Either[ResourceError, Unit] = {
            checkContentType(contentType) match {
                case Left(e) => Left(e)
                case Right(contentType) => {
                    moveResourceFileTo(uuid, ref, configuration.resource.path) match {
                        case Left(e) => Left(e)
                        case Right(newRef) => {
                            resourceDAO.getResourceById(uuid) match {
                                case Some(resource) => { 
                                    val newResource = resource.copy(
                                        category = Some(contentType),
                                        path = Some(newRef.path.toString),
                                        edition_date = Some(Instant.now.getEpochSecond.toInt)
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

        def moveResourceFileTo(uuid: UUID, ref: TemporaryFile, to: String): Either[ResourceError, TemporaryFile] = {
            Try {
                ref.moveTo(new File(s"${to}/${uuid.toString}"))
            } match {
                case Failure(e) => {
                    Logger.info(s"Can't move to " + Paths.get(s"${to}/${uuid.toString}").toAbsolutePath.toString)
                    e.printStackTrace
                    Left(ResourceCantBeMoved)
                }
                case Success(newFile) => Right(newFile)
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
                    'uuid -> resource.uuid,
                    'category -> resource.category,
                    'color -> resource.color,
                    'lat -> resource.lat,
                    'lng -> resource.lng,
                    'path -> resource.path,
                    'creation_date -> resource.creation_date,
                    'deletion_date -> resource.deletion_date,
                    'edition_date -> resource.edition_date,
                    'managed_date -> resource.managed_date,
                    'validator -> resource.validator.get.uuid
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
                    'category -> resource.category,
                    'color -> resource.color,
                    'lat -> resource.lat,
                    'lng -> resource.lng,
                    'path -> resource.path,
                    'creation_date -> resource.creation_date,
                    'deletion_date -> resource.deletion_date,
                    'edition_date -> resource.edition_date,
                    'managed_date -> resource.managed_date,
                    'validator -> resource.validator.get.uuid
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