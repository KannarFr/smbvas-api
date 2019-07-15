package models

import java.time.ZonedDateTime

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

object analytics_module {
  case class AnalyticsEntry(
    ip: String,
    lastAccessDate: ZonedDateTime,
    nbAccess: Int
  )
  implicit val analyticsEntryFormat = Json.format[AnalyticsEntry]

  sealed abstract class AnalyticsEntryError
  case object AnalyticsEntryAlreadyPresent extends AnalyticsEntryError
  case object UnhandledException extends AnalyticsEntryError

  object AnalyticsEntry {
    implicit val analyticsEntryPgEntity: PgEntity[AnalyticsEntry] = new PgEntity[AnalyticsEntry] {
      val tableName = "analytics"

      val columns = List(
        PgField("ip", Some("text"), true), PgField("last_access"), PgField("nb_access_to_map")
      )

      def parser(prefix: String): RowParser[AnalyticsEntry] = {
        get[String]("ip") ~
        get[ZonedDateTime]("last_access") ~
        get[Int]("nb_access_to_map") map {
          case (ip ~ lastAccessDate ~ nbAccess) =>
            AnalyticsEntry(ip, lastAccessDate, nbAccess)
        }
      }
    }
  }

  class AnalyticsDAO @Inject()(db: Database) {
    import AnalyticsEntry._

    def pushAccessFor(ip: String): Either[AnalyticsEntryError, Unit] = db.withConnection { implicit c =>
      Try {
        SQL(selectSQL[AnalyticsEntry] + " where ip = {ip}")
          .on('ip -> ip)
          .as(parser[AnalyticsEntry]().singleOpt)
          .map { entry =>
            SQL(updateSQL[AnalyticsEntry](List.empty))
              .on(
                'ip -> entry.ip,
                'last_access -> ZonedDateTime.now,
                'nb_access_to_map -> (entry.nbAccess + 1)
              )
              .executeUpdate
            ()
          }.getOrElse {
            SQL(insertSQL[AnalyticsEntry])
              .on(
                'ip -> ip,
                'last_access -> ZonedDateTime.now,
                'nb_access_to_map -> 1,
              )
              .execute
            ()
          }
      } match {
        case Failure(e) => {
          Logger.error("Something wrong happened while patching a user")
          e.printStackTrace
          Left(UnhandledException)
        }
        case Success(s) => Right(s)
      }
    }
  }
}
