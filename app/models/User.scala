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

object user_module {
  case class User(
    uuid: UUID,
    email: String,
    password: String
  )

  sealed abstract class UserError
  case object UserAlreadyPresent extends UserError
  case object UnhandledException extends UserError

  object User {
    implicit val userFormat = Json.format[User]
    implicit val userPgEntity: PgEntity[User] = new PgEntity[User] {
      val tableName = "users"

      val columns = List(
        PgField("uuid"), PgField("email"), PgField("password")
      )

      def parser(prefix: String): RowParser[User] = {
        get[UUID]("uuid") ~
        get[String]("email") ~
        get[String]("password") map {
          case (uuid ~ email ~ password) =>
            User(uuid, email, password)
        }
      }
    }
  }

  class UserDAO @Inject()(db: Database) {
    import User._

    def getUsers: List[User] = db.withConnection { implicit c =>
      SQL(selectSQL[User]) as (parser[User]().*)
    }

    def getUserById(uuid: UUID): Option[User] = db.withConnection { implicit c =>
      SQL(selectSQL[User] + " WHERE uuid = {uuid}").on(
        'uuid -> uuid
      ).as(parser[User]().singleOpt)
    }

    def patchUser(user: User): Either[UserError, Unit] = db.withConnection { implicit c =>
      Try {
        SQL(updateSQL[User](List("uuid"))).on(
          'email -> user.email,
          'password -> user.password
        ).executeUpdate
        ()
      } match {
        case Failure(e) => {
          Logger.error("Something wrong happened while patching a user")
          e.printStackTrace
          Left(UnhandledException)
        }
        case Success(s) => Right(s)
      }
    }

    def create(user: User): Either[UserError, Unit] = db.withConnection { implicit c =>
      Try {
        SQL(insertSQL[User]).on(
          'uuid -> user.uuid,
          'email -> user.email,
          'password -> user.password
        ).execute
        ()
      } match {
        case Failure(e: org.postgresql.util.PSQLException) => {
          if(e.getSQLState == "23505") {
            Left(UserAlreadyPresent)
          } else {
            Logger.error("Something wrong happened while inserting a user")
            e.printStackTrace
            Left(UnhandledException)
          }
        }
        case Failure(e) => {
          Logger.error("Something wrong happened while inserting a user")
          e.printStackTrace
          Left(UnhandledException)
        }
        case Success(s) => Right(s)
      }
    }
  }
}
