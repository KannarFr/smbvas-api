package models

import java.io._
import java.net.URL
import java.nio.file._
import java.util.UUID
import javax.inject._

import scala.util.{Try, Success, Failure}

import play.api.libs.json._
import play.api.libs.Files._
import play.api.Logger

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.ClientConfiguration
import com.amazonaws.Protocol
import com.amazonaws.services.s3.model._

import utils.Configuration

object cellar_module {
  implicit val urlWrites = new Writes[URL] {
    def writes(url: URL) = Json.obj("url" -> url.toString)
  }

  class CellarDTO @Inject()(conf: Configuration) {
    val s3Client = new AmazonS3Client(
      new BasicAWSCredentials(conf.cellar.access_key, conf.cellar.secret_key),
      (new ClientConfiguration).withProtocol(Protocol.HTTP)
    )
    s3Client.setEndpoint(conf.cellar.host)
    val bucketName = conf.cellar.bucket_name

    def createResourceFile(uuid: UUID, file: TemporaryFile): Either[_, Unit] = {
      Try(s3Client.putObject(
          new PutObjectRequest(bucketName, uuid.toString, file)
            .withCannedAcl(CannedAccessControlList.PublicRead)
      )) match {
        case Failure(e) => Left(e)
        case Success(_) => Right(())
      }
    }

    def getResourceUrl(uuid: UUID): Either[_, URL] = {
      Try(s3Client.getUrl(bucketName, uuid.toString)) match {
        case Failure(e) => Left(e)
        case Success(url) => Right(url)
      }
    }

    def deleteResourceFile(uuid: UUID): Either[_, Unit] = {
      Try(s3Client.deleteObject(bucketName, uuid.toString)) match {
        case Failure(e) => Left(e)
        case Success(_) => Right(())
      }
    }
  }
}
