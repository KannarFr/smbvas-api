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

import models.user_module._

object token_module {
  case class Token(
    value: UUID,
    userId: UUID,
    expireDate: ZonedDateTime
  )
  implicit val tokenWriter = Json.writes[Token]

  sealed abstract class TokenError
  case object TokenAlreadyPresent extends TokenError
  case object UnhandledException extends TokenError

  object Token {
    def generateFor(user: User): Token = {
      Token(
        value = UUID.randomUUID,
        userId = user.id,
        expireDate = ZonedDateTime.now.plusYears(10)
      )
    }

    implicit val tokenPgEntity: PgEntity[Token] = new PgEntity[Token] {
      val tableName = "token"

      val columns = List(
        PgField("value"), PgField("user_id"), PgField("expire_date")
      )

      def parser(prefix: String): RowParser[Token] = {
        get[UUID]("value") ~
        get[UUID]("user_id") ~
        get[ZonedDateTime]("expire_date") map {
          case (value ~ userId ~ expireDate) =>
            Token(value, userId, expireDate)
        }
      }
    }
  }

  class TokenDAO @Inject()(db: Database) {
    import User._

    def getTokenByValue(value: UUID): Option[Token] = db.withConnection { implicit c =>
      SQL(selectSQL[Token] + " WHERE value = {value}").on(
        'value -> value
      ).as(parser[Token]().singleOpt)
    }

    def create(token: Token): Either[TokenError, Unit] = db.withConnection { implicit c =>
      Try {
        SQL(deleteSQL[Token]).on(
          'user_id -> token.userId
        ).execute
        SQL(insertSQL[Token]).on(
          'value -> token.value,
          'user_id -> token.userId,
          'expire_date -> token.expireDate
        ).execute
        ()
      } match {
        case Failure(e: org.postgresql.util.PSQLException) => {
          if(e.getSQLState == "23505") {
            Left(TokenAlreadyPresent)
          } else {
            Logger.error("Something wrong happened while inserting a token")
            e.printStackTrace
            Left(UnhandledException)
          }
        }
        case Failure(e) => {
          Logger.error("Something wrong happened while inserting a token")
          e.printStackTrace
          Left(UnhandledException)
        }
        case Success(s) => Right(s)
      }
    }
  }
}
