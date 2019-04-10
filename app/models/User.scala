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

import models.token_module._

object user_module {
  case class UserToAuthenticate(
    email: String,
    password: String
  )
  implicit val userToAuthenticateFormat = Json.format[UserToAuthenticate]

  case class User(
    id: UUID,
    email: String,
    password: String
  )
  implicit val userFormat = Json.format[User]

  sealed abstract class UserError
  case object UserAlreadyPresent extends UserError
  case object UnhandledException extends UserError

  object User {
    implicit val userPgEntity: PgEntity[User] = new PgEntity[User] {
      val tableName = "user"

      val columns = List(
        PgField("id"), PgField("email"), PgField("password")
      )

      def parser(prefix: String): RowParser[User] = {
        get[UUID]("id") ~
        get[String]("email") ~
        get[String]("password") map {
          case (id ~ email ~ password) =>
            User(id, email, password)
        }
      }
    }
  }

  class UserDAO @Inject()(db: Database, tokenDAO: TokenDAO) {
    import User._

    def getUsers: List[User] = db.withConnection { implicit c =>
      SQL(selectSQL[User]) as (parser[User]().*)
    }

    def authenticate(user: UserToAuthenticate): Either[String, Token] = db.withConnection { implicit c =>
      SQL(selectSQL[User] + " WHERE email = {email}")
        .on('email -> user.email)
        .as(parser[User]().singleOpt)
        .map { userAuthenticated =>
          if (userAuthenticated.password == user.password) {
            val token = Token.generateFor(userAuthenticated)
            tokenDAO.create(token) match {
              case Left(e) => Left(e.toString)
              case Right(_) => Right(token)
            }
          } else {
            Left("Wrong password")
          }
        }.getOrElse(Left("User does not exist"))
    }

    def getUserById(id: UUID): Option[User] = db.withConnection { implicit c =>
      SQL(selectSQL[User] + " WHERE id = {id}").on(
        'id -> id
      ).as(parser[User]().singleOpt)
    }

    def patchUser(user: User): Either[UserError, Unit] = db.withConnection { implicit c =>
      Try {
        SQL(updateSQL[User](List("id"))).on(
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
          'id -> user.id,
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
