package models

import java.net.URL
import javax.inject._

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.ClientConfiguration
import com.amazonaws.Protocol
import com.amazonaws.services.s3.model._
import play.api.libs.Files._
import java.nio.file._
import java.io._
import java.util.UUID
import play.api.Logger
import scala.util.{Try, Success, Failure}

import utils.Configuration

object cellar_module {
	class CellarDTO @Inject()(conf: Configuration) {
		val s3Client = new AmazonS3Client(
			new BasicAWSCredentials(conf.cellar.access_key, conf.cellar.secret_key),
			(new ClientConfiguration).withProtocol(Protocol.HTTP)
		)
		s3Client.setEndpoint(conf.cellar.host)
		val bucketName = conf.cellar.bucket_name

		def createResourceFile(uuid: UUID, file: TemporaryFile): Either[_, Unit] = {
			Try {
				s3Client.putObject(
					new PutObjectRequest(bucketName, uuid.toString, file)
						.withCannedAcl(CannedAccessControlList.PublicRead)
				)
			} match {
				case Failure(e) => {
					Logger.error(e.getMessage)
					Left(e)
				}
				case Success(_) => {
					Logger.info(s"${uuid.toString} created")
					Right(())
				}
			}
		}

		def getResourceUrl(uuid: UUID): Either[_, URL] = {
			Try {
				s3Client.getUrl(bucketName, uuid.toString)
			} match {
				case Failure(e) => {
					Logger.error(e.getMessage)
					Left(e)
				}
				case Success(url) => {
					Right(url)
				}
			}
		}

		def deleteResourceFile(uuid: UUID): Either[_, Unit] = {
			Try {
				s3Client.deleteObject(bucketName, uuid.toString)
			} match {
				case Failure(e) => {
					Logger.error(e.getMessage)
					Left(e)
				}
				case Success(_) => {
					Logger.info(s"${uuid.toString} deleted")
					Right(())
				}
			}
		}
	}
}